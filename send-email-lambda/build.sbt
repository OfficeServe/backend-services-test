name := "send-email"

libraryDependencies ++= {
    Seq(
      "com.amazonaws" % "aws-lambda-java-core" % "1.1.0",
      "com.amazonaws" % "aws-lambda-java-events" % "1.1.0",
      "com.amazonaws" % "aws-java-sdk-ses" % "1.11.23",
      "com.amazonaws" % "aws-java-sdk-s3" % "1.11.23",
      "com.github.dwhjames" %% "aws-wrap" % "0.8.0",
      "org.json4s" %% "json4s-jackson" % "3.3.0",
      "com.typesafe" % "config" % "1.3.0",
      "javax.mail" % "mail" % "1.4.7",
      "org.scalatest" %% "scalatest" % "2.2.4" % "test",
      "org.mockito" % "mockito-core" % "1.10.19" % "test"
    )
}
