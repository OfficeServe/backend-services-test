# Report Service

Installation
------------

You first need to setup Amazon SNS to work with SQS (more info [here](http://docs.aws.amazon.com/sns/latest/dg/SendMessageToSQS.html#SendMessageToSQS.sqs.permissions))

####1. Create a SQS queue
The first step is to create a queue as it is [shown here](http://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSGettingStartedGuide/CreatingQueue.html)
A queue for dev environment has been already created as `dev_report-service-queue` 

####2. Subscribe the SQS queue to an SNS topic
Using the AWS Management Console you can subscribe the queue to a topic as it is [shown here](http://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqssubscribe.html)
You should subscribe the queue to the Orders topic. For dev, this topic is `dev_orders`


Notes on the code
-----------------
Actor factories have been created in order to inject a TestProbe instead of child actors. See [here](http://christopher-batey.blogspot.co.uk/2014/02/akka-testing-messages-sent-to-child.html) for more details