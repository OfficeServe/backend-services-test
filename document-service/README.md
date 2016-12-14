#Document Service

A service that generates a PDF document, given a template and some input data, and stores it in S3


Run the application locally
---------------------------

In order to run the application locally, make sure you run a local S3 docker container:

`docker-compose up`

Also, make sure you setup the following environment variables in your bash or IDE

`export AWS_ACCESS_KEY_ID="accessKey1"`
`export AWS_SECRET_KEY="verySecretKey1"`

or run the included `setup-local.sh` script, that will do it for you (you still need to put those in your IDE config)

`. ./setup-local.sh`


You can run the application by calling
`sbt run`