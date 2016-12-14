name := """report-service"""

libraryDependencies ++= {
  val akkaVersion = "2.4.14"
  val akkaHttpVersion = "10.0.0"
  val awsJavaSdkVersion = "1.11.52"
  val json4sVersion = "3.4.2"
  Seq(
    "com.typesafe.akka" %% "akka-actor" % akkaVersion,
    "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
    "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
    "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion,
    "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0",
    "ch.qos.logback" % "logback-classic" % "1.1.7",
    "org.slf4j" % "jcl-over-slf4j" % "1.7.21",
    "com.amazonaws" % "aws-java-sdk-sqs" % awsJavaSdkVersion,
    "com.amazonaws" % "aws-java-sdk-sns" % awsJavaSdkVersion,
    "com.amazonaws" % "aws-java-sdk-dynamodb" % awsJavaSdkVersion,
    "com.amazonaws" % "aws-java-sdk-core" % awsJavaSdkVersion,
    "org.json4s" %% "json4s-core" % json4sVersion,
    "org.json4s" %% "json4s-jackson" % json4sVersion,
    "net.debasishg" %% "redisclient" % "3.2",
    "officeserve.commons" %% "dynamodb-support" % "0.0.1",
    "officeserve.services.document-service" %% "document-service-api" % "0.0.11",
    "officeserve.services.basket-service" %% "basket-service-model" % "0.1.23" intransitive(),
    "officeserve.services.send-email" %% "send-email-model" % "0.0.1",
    "org.scalatest" %% "scalatest" % "3.0.0" % "test",
    "org.mockito" % "mockito-core" % "2.2.8" % "test",
    "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test"
  )
}
