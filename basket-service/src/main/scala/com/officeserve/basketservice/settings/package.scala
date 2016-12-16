package com.officeserve.basketservice

import com.amazonaws.services.sns.AmazonSNSAsyncClient
import com.github.dwhjames.awswrap.sns.AmazonSNSScalaClient
import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ValueReader
import officeserve.commons.domain.MoneyDom
import net.ceedubs.ficus.readers.ArbitraryTypeReader._

import scala.concurrent.duration.FiniteDuration

package settings {

  case class ServerSettings(host: String, port: Int, timeout: FiniteDuration) {

    def this(config: Config) = {
      this(config.as[String]("server.http.host"),
        config.as[Int]("server.http.port"),
        config.as[FiniteDuration]("server.startup.timeout"))
    }

  }

  case class BasketConfig(tableTopMinimumOrder: Option[MoneyDom], extraTablewarePercent: Double, tableTopCutOffTime: String, tableTopDeliveryCharge:MoneyDom) {

    require(tableTopMinimumOrder.forall(_.amount.isPositiveOrZero()),
      "Negative minimum order value does not make sense")

    def this(config: Config) = {
      this(config.as[Option[MoneyDom]]("basket.config.tableTopMinimumOrder"),
        config.as[Double]("basket.config.extraTablewarePercent"),
        config.as[String]("basket.config.tableTopCutOffTime"),
        config.as[MoneyDom]("basket.config.tableTopDeliveryCharge"))
    }
  }

  case class NotificationsSettings(ordersCompleteTopicArn: String, endpoint: String) {

    def this(config: Config) = {
      this(config.as[String]("notifications.sns.orders_complete_topic_arn"),
        config.as[String]("notifications.sns.endpoint"))
    }

    val snsClient = {
      val javaClient = new AmazonSNSAsyncClient().withEndpoint[AmazonSNSAsyncClient](endpoint)
      new AmazonSNSScalaClient(javaClient)
    }
  }

  case class SpreedlySettings(gatewayToken: String, environmentToken: String, accessSecret: String) {

    def this(config: Config) = {
      this(config.as[String]("spreedly.gatewayToken"),
        config.as[String]("spreedly.environmentToken"),
        config.as[String]("spreedly.accessSecret"))
    }
  }

  case class CatalogueClientSettings(baseUrl: String) {

    def this(config: Config) = {
      this(config.as[String]("catalogue.client.baseUrl"))
    }
  }

  case class SQSSettings(region: String, queueUrl: String, maxNumberOfMessages: Int = 10)

  object SQSSettings {

    def create(config: Config):SQSSettings =
      config.as[SQSSettings]("message.consumer")
  }


}

package object settings {

  implicit val moneyDomValueReader: ValueReader[MoneyDom] =
    implicitly[ValueReader[BigDecimal]].map(MoneyDom.asJodaMoney(_))

}
