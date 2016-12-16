# basket-service
Microservice responsible for managing basket

# How to run the tests

1. (Only on Linux) `export AWS_DYNAMODB_ENDPOINT=http://172.18.0.1:8000`
2. `sbt dockerComposeTest`. Alternatively, you can do `sbt dockerComposeUp` or similar,
   and then run the tests normally.
   
### Advanced testing 
The advanced testing directory src/advancedTesting contains two sub-directories, one for the integration tests "it" and one
for the end2end tests "end2end".you can run tests in these directories with "sbt it:test" and "sbt e2e:test"

