# send-email-lambda

An AWS Lambda function to send emails using Amazon SES.

The function will react to messages with the following payload:

```json
{  
  "eventType": "SendEmail",  
  "entities": [{      
    "from": "support@officeserve.com",      
    "to": ["n.cavallo@officeserve.com"],      
    "subject": "Hi, this is a test",      
    "body": "Still a test <h1> A TEST! </h1>",      
    "format": "html", 
    "attachments": ["https://s3-eu-west-1.amazonaws.com/testing-document/2016/11/03/KYM-9251234-invoice.pdf"]  
  }]
}
```


## Installation

1. Create an IAM Role for executing AWS Lambda functions.
2. Give your new IAM Role the following policy:

*Note: replace "<DOCUMENTS_BUCKET>" for the name of the bucket where document service is storing the files to be attached.*

```json
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Action": [
                "logs:CreateLogGroup",
                "logs:CreateLogStream",
                "logs:PutLogEvents"
            ],
            "Resource": "arn:aws:logs:*:*:*"
        },
        {
            "Effect": "Allow",
            "Action": [
                "cloudwatch:PutMetricData"
            ],
            "Resource": "*"
        },
        {
            "Effect": "Allow",
            "Action": [
                "ses:SendEmail",
                "ses:SendRawEmail"
            ],
            "Resource": "*"
        },
        {
            "Effect": "Allow",
            "Action": [
                "s3:GetObject"
            ],
            "Resource": [
                "arn:aws:s3:::<DOCUMENTS_BUCKET>/*"
            ]
        }
    ]
}
```
