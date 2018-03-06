# DEWE.v0

DEWE.v0 is a very simple scentific workflow execution engine developed with the producer/consumer model. We make this project available as an entry level introduction to researchers who are interested in building distributed systems to execute scientific workflows. For full-featured workflow execution engines, you should look into [DEWE.v2](https://github.com/qyjohn/DEWE.v2) and [DEWE.v3](https://github.com/qyjohn/DEWE.v3) instead.

In short, this system includes a producer (the workflow scheduler) and a consumer (the local worker), with two queues (Amazon SQS) in between. The workflow scheduler (WorkflowScheduler.java) takes a workflow definition, then dispatches jobs that are elegible to run to a job queue. The local worker (LocalWorker.java) polls the job queue for jobs to run, then sends acknowledge messages to an ack queue. The workflow scheduler polls the ack queue to know which jobs are completed, and dispatches others jobs to the job queue when they are elegible to run.

To build this demo, you need to have a recent version of the Java Developemnt Kit (JDK), along with Maven. 

~~~~
git clone https://github.com/qyjohn/DEWE.v0
cd DEWE.v0
mvn package
~~~~

To run this demo, we recommend that you launch two or more EC2 instances, with the AWS CLI installed. One EC2 instance runs the workflow scheduler, one or more EC2 instances run the local worker. Both EC2 instance should have an IAM role with full access to Amazon S3 and SQS. (You can of course run both the workflow scheduler and the local worker on the same EC2 instance in two separate SSH windows.)

On the workflow scheduler node, create an SQS queue. Here we assume that you are using the N. Virginia (us-east-1) region. If not, you need to replace the region name in the commands. Edit config.properties, replace the value for "queueUrl" with the URL you see in the following command output. Please note that you should have the same config.properties file on both EC2 instances. (We mentioned that DEWE.v0 needs two SQS queues, but we are only creating one (the ACK queue) here. The other one (the job queu) will be automatically created and terminated by the workflow scheduler when it runs.)

~~~~
aws sqs create-queue --queue-name dewev0-ack --region us-east-1
aws sqs list-queues --region us-east-1
~~~~

Create an S3 bucket to host the SimpleDemo workflow.

~~~~
aws s3 mb s3://<my-bucket> --region us-east-1
aws s3 cp --recursive SimpleDemo s3://<my-bucket>/SimpleDemo/ --region us-east-1
~~~~

The structure of the SimpleDemo workflow looks like this:

![SimpleDemo](http://www.qyjohn.net/wp-content/uploads/2015/08/屏幕快照-2015-08-16-下午3.42.48.png)

Below is the DAG definition for the SimpleDemo workflow:

~~~~
<?xml version="1.0" encoding="UTF-8"?>
<adag xmlns="http://pegasus.isi.edu/schema/DAX"
      xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
      xsi:schemaLocation="http://pegasus.isi.edu/schema/DAX http://pegasus.isi.edu/schema/dax-2.1.xsd"
      version="2.1" count="1" index="0" name="montage">

  <!-- Part 1:  Files Used -->

  <filename file="a.dat" link="inout"/>
  <filename file="b.dat" link="inout"/>
  <filename file="c.dat" link="output"/>

  <!-- Part 2:  Definition of Jobs -->

  <job id="ID000001" name="Sleep">
    <argument>
    </argument>
  </job>

  <job id="ID000002" name="SleepRecord">
    <argument>
      <file name="a.dat"/>
    </argument>

    <uses name="a.dat" link="output" transfer="true"/>
  </job>

  <job id="ID000003" name="SleepRecord">
    <argument>
      <file name="b.dat"/>
    </argument>

    <uses name="b.dat" link="output" transfer="true"/>
  </job>

  <job id="ID000004" name="Add">
    <argument>
      <file name="a.dat"/>
      <file name="b.dat"/>
      <file name="c.dat"/>
    </argument>

    <uses name="a.dat" link="input" transfer="true"/>
    <uses name="b.dat" link="input" transfer="true"/>
    <uses name="c.dat" link="output" transfer="false"/>
  </job>

  <!-- Part 3:  Precedence Requirements -->

  <child ref="ID000002">
    <parent ref="ID000001"/>
  </child>

  <child ref="ID000003">
    <parent ref="ID000001"/>
  </child>

  <child ref="ID000004">
    <parent ref="ID000002"/>
    <parent ref="ID000003"/>
  </child>

</adag>
~~~~

Start the workflow scheduler:

~~~~
java -cp target/dewev0-1.0-SNAPSHOT.jar:. net.qyjohn.dewev0.WorkflowScheduler <my-bucket> SimpleDemo
~~~~

On the local worker node(s), run the local worker:

~~~~
cd DEWE.v0
java -cp target/dewev0-1.0-SNAPSHOT.jar:. net.qyjohn.dewev0.LocalWorker 
~~~~

When you are done with your experiments, please remember to delete your SQS queue and terminate the EC2 instances to avoid further charges.

~~~~
aws sqs delete-queue --queue-name dewev0-ack --region us-east-1
~~~~

We have also prepared a 0.20-degree Montage workflow for demo. You can run this with the following commands on the workflow scheduler node:


~~~~
cd DEWE.v0
aws s3 cp --recursive Montage_0.2 s3://<my-bucket>/Montage_0.2/ --region us-east-1
java -cp target/dewev0-1.0-SNAPSHOT.jar:. net.qyjohn.dewev0.WorkflowScheduler <my-bucket> Montage_0.2
~~~~


**References**

Qingye Jiang, Young Choon Lee, Albert Y. Zomaya, “Executing Large Scale Scientific Workflow Ensembles in Public Clouds“, The 44th International Conference on Parallel Processing (ICPP 2015), Beijing, September 2015

Qingye Jiang, Young Choon Lee, Albert Y. Zomaya, “Serverless Execution of Scientific Workflows”, The 15th International Conference on Service-Oriented Computing (ICSOC 2017), 2017.11, Malaga, Spain, 2017.11