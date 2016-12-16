package com.officeserve.basketservice.common

import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsyncClient
import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._

import scala.concurrent.duration.FiniteDuration

case class DynamoDBSettings(endpoint: String, tablePrefix: String, migrationTimeout: FiniteDuration, credentials: Option[BasicAWSCredentials]) {

  def this(config: Config, credentials: Option[BasicAWSCredentials] = None) = {
    this(config.as[String]("db.dynamo.endpoint"),
      config.as[String]("db.dynamo.tablePrefix"),
      config.as[FiniteDuration]("db.dynamo.migrations.timeout"),
      credentials)
  }

  val client = credentials.map(cr => new AmazonDynamoDBAsyncClient(cr).withEndpoint[AmazonDynamoDBAsyncClient](endpoint))
    .getOrElse(new AmazonDynamoDBAsyncClient().withEndpoint[AmazonDynamoDBAsyncClient](endpoint))


}
