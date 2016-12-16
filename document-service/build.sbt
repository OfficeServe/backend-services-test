name := """document-service"""

val akkaVersion = "2.4.11"
val json4sVersion = "3.4.1"

libraryDependencies ++= {
  Seq(
    "com.typesafe.akka" %% "akka-http-experimental" % akkaVersion,
    "com.typesafe.akka" %% "akka-http-testkit" % akkaVersion,
    "org.json4s" %% "json4s-core" % json4sVersion,
    "org.json4s" %% "json4s-jackson" % json4sVersion,
    "de.heikoseeberger" %% "akka-http-json4s" % "1.10.1",
    "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0",
    "ch.qos.logback" % "logback-classic" % "1.1.7",
    "org.slf4j" % "jcl-over-slf4j" % "1.7.21",
    "io.github.cloudify" %% "spdf" % "1.3.1",
    "com.amazonaws" % "aws-java-sdk-s3" % "1.11.39",
    "com.github.spullara.mustache.java" % "compiler" % "0.9.4",
    "com.github.spullara.mustache.java" % "scala-extensions-2.11" % "0.9.4",
    "org.scalatest" %% "scalatest" % "3.0.0" % "test",
    "org.mockito" % "mockito-core" % "2.1.0" % "test",
    "org.apache.pdfbox" % "pdfbox" % "2.0.3" % "test"
  )
}

dockerfile in docker := {
  // The assembly task generates a fat JAR file
  val artifact: File = assembly.value
  val artifactTargetPath = s"/app/${artifact.name}"

  new Dockerfile {
    from("anapsix/alpine-java")
    add(artifact, artifactTargetPath)
    entryPoint("java", "-jar", artifactTargetPath)
  }
}
