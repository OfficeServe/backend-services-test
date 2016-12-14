name := "common-spray"

libraryDependencies ++= {
  val sprayV = "1.3.3"
  val akkaV = "2.4.6"
  Seq(
    "io.spray" %% "spray-can" % sprayV withSources() withJavadoc(),
    "io.spray" %% "spray-routing-shapeless2" % sprayV withSources() withJavadoc(),
    "io.spray" %% "spray-http" % sprayV,
    "io.spray" %% "spray-httpx" % sprayV,
    "io.spray" %% "spray-util" % sprayV,
    "io.spray" %% "spray-json" % "1.3.2",
    "com.typesafe.akka" %% "akka-actor" % akkaV,
    "com.typesafe.scala-logging" %% "scala-logging" % "3.4.0",
    "ch.qos.logback" % "logback-classic" % "1.1.7",
    "io.spray" %% "spray-testkit" % "1.3.1" % "test" withSources() withJavadoc(),
    "org.scalatest" %% "scalatest" % "2.2.4" % "test"
  )
}
