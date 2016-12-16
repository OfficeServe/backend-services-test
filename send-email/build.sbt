name := "send-email"

libraryDependencies ++= {
    Seq(
      "com.amazonaws" % "aws-lambda-java-core" % "1.1.0" exclude("commons-logging","commons-logging"),
      "com.amazonaws" % "aws-lambda-java-events" % "1.1.0" exclude("commons-logging","commons-logging"),
      "com.amazonaws" % "aws-java-sdk-ses" % "1.11.68" exclude("commons-logging","commons-logging"),
      "com.amazonaws" % "aws-java-sdk-s3" % "1.11.68" exclude("commons-logging","commons-logging"),
      "com.github.dwhjames" %% "aws-wrap" % "0.8.0" exclude("commons-logging","commons-logging"),
      "org.json4s" %% "json4s-jackson" % "3.3.0",
      "com.typesafe" % "config" % "1.3.0",
      "javax.mail" % "mail" % "1.4.7",
      "org.scalatest" %% "scalatest" % "2.2.4" % "test",
      "org.mockito" % "mockito-core" % "1.10.19" % "test"
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
