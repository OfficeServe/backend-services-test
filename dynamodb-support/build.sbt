name := """dynamodb-support"""

scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
  "com.gu" %% "scanamo" % "0.8.2",
  "org.scalatest" %% "scalatest" % "3.0.1" % "test"
)
