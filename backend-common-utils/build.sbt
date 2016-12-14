name := "backend-common-utils"

libraryDependencies ++= {
  Seq(
    "com.typesafe.scala-logging" %% "scala-logging" % "3.4.0",
    "ch.qos.logback" % "logback-classic" % "1.1.7",
    "org.scalatest" %% "scalatest" % "2.2.4" % "test",
    "org.joda" % "joda-money" % "0.11"
  )
}
