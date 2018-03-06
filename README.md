# DEWE.v0

DEWE.v0 is a very simple workflow execution engine developed with the producer/consumer model. We make this project available as an entry level introduction to researchers who are interested in building distributed systems to execute scientific workflows. 

In short, this system includes a producer (the workflow scheduler) and a consumer (the local worker), with two queues (Amazon SQS) in between. The workflow scheduler (WorkflowScheduler.java) takes a workflow definition, then dispatches jobs that are elegible to run to a job queue. The local worker (LocalWorker.java) polls the job queue for jobs to run, then sends acknowledge messages to an ack queue. The workflow scheduler polls the ack queue to know which jobs are completed, and dispatches others jobs to the job queue when they are elegible to run.

To build this demo, you need to have a recent version of the Java Developemnt Kit (JDK), along with Maven. 

~~~~
git clone https://github.com/qyjohn/DEWE.v0
cd DEWE.v0
mvn package
~~~~

To run this demo, we recommend that you launch two EC2 instances, with the AWS CLI installed. One EC2 instance runs the workflow scheduler, one EC2 instance runs the local worker. Both EC2 instance should have an IAM role with full access to Amazon S3 and SQS. 

On the workflow scheduler node, create an SQS queue. Here we assume that you are using the N. Virginia (us-east-1) region. If not, you need to replace the region name in the commands. Edit config.properties, replace the value for "queueUrl" with the URL you see in the following command output. Please note that you should have the same config.properties file on both EC2 instances.

~~~~
aws sqs create-queue --queue-name dewev0 --region us-east-1
aws sqs list-queues --region us-east-1
~~~~

Create an S3 bucket to host the SimpleDemo workflow.

~~~~
aws s3 mb s3://<my-bucket> --region us-east-1
aws s3 cp --recursive SimpleDemo s3://<my-bucket>/SimpleDemo/ --region us-east-1
~~~~

The structure of the SimpleDemo workflow looks like this:

![SimpleDemo](http://www.qyjohn.net/wp-content/uploads/2015/08/屏幕快照-2015-08-16-下午3.42.48.png)

Start the workflow scheduler:

~~~~
java -cp target/dewev0-1.0-SNAPSHOT.jar:. net.qyjohn.dewev0.WorkflowScheduler <my-bucket> SimpleDemo
~~~~

On the local worker node, run the local worker:

~~~~
cd DEWE.v0
java -cp target/dewev0-1.0-SNAPSHOT.jar:. net.qyjohn.dewev0.LocalWorker 
~~~~