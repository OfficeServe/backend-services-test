
import sbt._
import sbt.Keys._
import SpecialConfigs.all
import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations._

name := "backend-services"

organization := "officeserve.services"

scalaVersion := "2.11.8"

lazy val commonSettings = Seq(
  organization := "officeserve.services.basket-service",
  scalaVersion := "2.11.8",
  scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")
)

lazy val commonDeps = Seq(

)

lazy val backendCommonUtils = (project in file("backend-common-utils"))
  .settings(commonSettings: _*)

lazy val commonSpray = (project in file("common-spray"))
  .settings(commonSettings: _*).enablePlugins(DockerPlugin)
  .dependsOn(backendCommonUtils)

lazy val basket = (project in file("basket-service"))
  .settings(commonSettings: _*).enablePlugins(DockerPlugin)
  .dependsOn(backendCommonUtils, commonSpray)

lazy val document = (project in file("document-service"))
  .settings(commonSettings: _*).enablePlugins(DockerPlugin)

lazy val sendEmail = (project in file("send-email"))
  .settings(commonSettings: _*).enablePlugins(DockerPlugin)
  .dependsOn(document, basket)

lazy val dynamodbSupport = (project in file("dynamodb-support"))
    .settings(commonSettings: _*)

lazy val report = (project in file("report-service"))
  .settings(commonSettings: _*)
  .dependsOn(document, basket, sendEmail, dynamodbSupport)

lazy val root = (project in file(".")).aggregate(commonSpray, backendCommonUtils, sendEmail, dynamodbSupport, basket, document, report)
