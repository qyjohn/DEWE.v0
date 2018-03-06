package net.qyjohn.dewev0;

import java.io.*;
import java.nio.*;
import java.util.*;
import com.amazonaws.services.s3.*;
import com.amazonaws.services.s3.model.*;
import com.amazonaws.services.sqs.*;
import com.amazonaws.services.sqs.model.*;
import org.apache.log4j.Logger;

public class WorkflowScheduler extends Thread
{
	public AmazonSQSClient sqsClient = new AmazonSQSClient();
	String longQueue, ackQueue;

	Workflow workflow;
	String uuid, s3Bucket, s3Prefix;
	boolean localExec, completed;
	
	final static Logger logger = Logger.getLogger(WorkflowScheduler.class);
	
	Date d1, d2;
	
	public WorkflowScheduler(String bucket, String prefix)
	{
		try
		{
			// Create uuid and ACK queue
			uuid = UUID.randomUUID().toString();
			CreateQueueResult result = sqsClient.createQueue(uuid);
			ackQueue = result.getQueueUrl();
			
			// System Properties
			Properties prop = new Properties();
			InputStream input = new FileInputStream("config.properties");
			prop.load(input);
			longQueue = prop.getProperty("queueUrl");
	
			// Parsing workflow definitions
			logger.info("Parsing workflow definitions...");
			localExec = true;
			workflow = new Workflow(uuid, bucket, prefix, localExec, ackQueue);
			completed  = false;	
			
			// Initial Dispatch
			initialDispatch();		
		} catch (Exception e)
		{
			System.out.println(e.getMessage());
			e.printStackTrace();				
		}
	}
	
	
	public void initialDispatch()
	{
		d1 = new Date();
		logger.info("Begin workflow execution.");
		for (WorkflowJob job : workflow.jobs.values())	
		{
			if (job.ready)
			{
				dispatchJob(job.jobId);
			}
		}	
	}
	
	
	/**
	 *
	 * Publishing a job to the jobStream for the worker node (a Lambda function) to pickup.
	 *
	 */
	 
	public void dispatchJob(String id)
	{
		WorkflowJob job = workflow.jobs.get(id);

		if (job != null)
		{
			logger.info("Dispatching " + job.jobId + ":\t" + job.jobName);
			boolean success = false;
			while (!success)
			{
				try
				{
					sqsClient.sendMessage(longQueue, job.jobXML);				
					success = true;
				} catch (Exception e) 
				{
					System.out.println(e.getMessage());
					e.printStackTrace();	
				}				
			}
		}		
	}
	
	
	/**
	 *
	 * The worker node (a Lambda function) sends an ACK message to the ackStream, indicating a particular job is now complete.
	 *
	 */
	 
	public void setJobAsComplete(String id)
	{		
		WorkflowJob job = workflow.jobs.get(id);

		if (job != null)
		{
			// Get a list of the children jobs
			for (String child_id : job.childrenJobs) 
			{
				// Get a list of the jobs depending on a particular output file
				WorkflowJob childJob = workflow.jobs.get(child_id);
				// Remove this depending parent job
				childJob.removeParent(id);
				if (childJob.ready)
				{
					dispatchJob(childJob.jobId);
				}
			}
			workflow.jobs.remove(id);
		}	
		
		if (workflow.isEmpty())
		{
			completed = true;
		}	
	}
	
	/**
	 *
	 * The run() method.
	 *
	 */
	 
	public void run()
	{
		while (!completed)
		{
			// Pulling the ackQueue
			try
			{
				ReceiveMessageResult result = sqsClient.receiveMessage(ackQueue);
				for (Message message : result.getMessages())
				{
					String job = message.getBody();
					logger.info(job + " is now completed.");
					setJobAsComplete(job);
					sqsClient.deleteMessage(ackQueue, message.getReceiptHandle());
				}				
			} catch (Exception e)
			{
			}
		}
		
		logger.info("Workflow is now completed.");
		d2 = new Date();
		long seconds = (d2.getTime()-d1.getTime())/1000;
		System.out.println("\n\nTotal execution time: " + seconds + " seconds.\n\n");
		sqsClient.deleteQueue(uuid);
	}
	
	
	class AckPuller extends Thread
	{
		public void run()
		{
			AmazonSQSClient c = new AmazonSQSClient();

			while (!completed)
			{
				// Pulling the ackQueue
				try
				{
					ReceiveMessageResult result = c.receiveMessage(ackQueue);
					for (Message message : result.getMessages())
					{
						String job = message.getBody();
						logger.info(job + " is now completed.");
						setJobAsComplete(job);
						c.deleteMessage(ackQueue, message.getReceiptHandle());
					}				
				} catch (Exception e)
				{
				}				
			}	
		}		
	}

	public static void main(String[] args)
	{
		try
		{
			WorkflowScheduler scheduler = new WorkflowScheduler(args[0], args[1]);
			scheduler.start();
		} catch (Exception e)
		{
			System.out.println(e.getMessage());
			e.printStackTrace();
		}
	}
}

