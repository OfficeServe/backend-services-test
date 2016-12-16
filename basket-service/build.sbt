name := "basket-service"

libraryDependencies ++= {
  val json4sV = "3.3.0"
  Seq(
    "net.codingwell" %% "scala-guice" % "4.0.1",
    "com.typesafe" % "config" % "1.3.0",
    "com.iheart" %% "ficus" % "1.4.0",
    "com.gu" %% "scanamo" % "0.6.0",
    "io.spray" %% "spray-client" % "1.3.3",
    "org.scalaz" %% "scalaz-core" % "7.1.0",
    "com.amazonaws" % "aws-java-sdk-sns" % "1.10.32" exclude("commons-logging","commons-logging"),
    "com.amazonaws" % "aws-java-sdk-sqs" % "1.10.32" exclude("commons-logging","commons-logging"),
    "com.github.dwhjames" %% "aws-wrap" % "0.8.0" exclude("commons-logging","commons-logging"),
    "org.json4s" %% "json4s-jackson" % json4sV,
    "org.json4s" %% "json4s-ext" % json4sV,
    "org.apache.poi" % "poi" % "3.8" % "test",
    "org.apache.poi" % "poi-ooxml" % "3.8" % "test",
    "com.ticketfly" %% "spreedly-client" % "1.1.0",
    "io.spray" %% "spray-testkit" % "1.3.1" % "test" withSources() withJavadoc(),
    "org.scalatest" %% "scalatest" % "2.2.4" % "test",
    "com.netaporter" %% "pre-canned" % "0.0.8" % "test",
    "org.mockito" % "mockito-core" % "1.10.19" % "test"
  )
}

resolvers += Resolver.bintrayRepo("dwhjames", "maven")

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
