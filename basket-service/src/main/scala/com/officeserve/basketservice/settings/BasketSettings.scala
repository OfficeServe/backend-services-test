package com.officeserve.basketservice.settings

import com.officeserve.basketservice.common.DynamoDBSettings
import com.typesafe.config.{Config, ConfigFactory}

class BasketSettings(config: Config = ConfigFactory.load()) {

  val serverSettings = new ServerSettings(config)

  val basketConfig = new BasketConfig(config)

  val notificationsSettings = new NotificationsSettings(config)

  val spreedlySettings = new SpreedlySettings(config)

  val dynamoDBSettings = new DynamoDBSettings(config)

  val catalogueClientSettings = new CatalogueClientSettings(config)

  val sqsSettings = SQSSettings.create(config)

}
