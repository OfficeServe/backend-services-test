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
    "com.amazonaws" % "aws-java-sdk-sqs" % awsJavaSdkVersion exclude("commons-logging","commons-logging"),
    "com.amazonaws" % "aws-java-sdk-sns" % awsJavaSdkVersion exclude("commons-logging","commons-logging"),
    "com.amazonaws" % "aws-java-sdk-dynamodb" % awsJavaSdkVersion exclude("commons-logging","commons-logging"),
    "com.amazonaws" % "aws-java-sdk-core" % awsJavaSdkVersion exclude("commons-logging","commons-logging"),
    "org.json4s" %% "json4s-core" % json4sVersion,
    "org.json4s" %% "json4s-jackson" % json4sVersion,
    "net.debasishg" %% "redisclient" % "3.2",
    "org.scalatest" %% "scalatest" % "3.0.0" % "test",
    "org.mockito" % "mockito-core" % "2.2.8" % "test",
    "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test"
  )
}

dockerfile in docker := {
  val appDir = stage.value
  val targetDir = "/app"

  new Dockerfile {
    from("anapsix/alpine-java")
    entryPoint(s"$targetDir/bin/${executableScriptName.value}")
    copy(appDir, targetDir)
  }
}

buildOptions in docker := BuildOptions(cache = false)
