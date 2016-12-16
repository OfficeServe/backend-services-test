package com.officeserve.basketservice.clients

import java.time.ZonedDateTime

import akka.actor.ActorSystem
import com.amazonaws.regions.Regions
import com.amazonaws.services.sqs.AmazonSQSAsyncClient
import com.amazonaws.services.sqs.model.Message
import com.github.dwhjames.awswrap.sqs.AmazonSQSScalaClient
import com.officeserve.basketservice.service.{OrderService, TradingDays}
import com.officeserve.basketservice.settings.SQSSettings
import com.officeserve.basketservice.web.Adapter
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

class SQSConsumer(sqsSettings: SQSSettings)
                 (implicit ec: ExecutionContext, tradingDays: TradingDays) extends Adapter with StrictLogging {

  val client = new AmazonSQSScalaClient(new AmazonSQSAsyncClient().withRegion(Regions.fromName(sqsSettings.region)), ec)

  private def receiveMessage(maxNumberOfMessages: Int = sqsSettings.maxNumberOfMessages) =
    client.receiveMessage(sqsSettings.queueUrl, maxNumberOfMessages)

  def consume(handler: PartialFunction[Message, Future[Unit]])(implicit ec: ExecutionContext = ExecutionContext.global): Unit = {
    receiveMessage() map { messages =>
      messages foreach { m =>
        logger.info("Processing message: " + m.getBody)
        if (handler.isDefinedAt(m)) {
          handler.apply(m) andThen {
            case Success(_) =>
              deleteMessage(m.getReceiptHandle)
            case Failure(e) =>
              logger.error("Error processing message", e)
          }
        }
      }
    }
  }

  private def deleteMessage(receiptHandle: String): Future[Unit] =
    client.deleteMessage(sqsSettings.queueUrl, receiptHandle)

}

sealed abstract class SQSMessageProcessor(sqsConsumer: SQSConsumer)
                                         (implicit system: ActorSystem, tradingDays: TradingDays)

  extends StrictLogging with Adapter {

  implicit val ec = system.dispatcher

  def handler: PartialFunction[Message, Future[Unit]]

  system.scheduler.schedule(0 minute, 1 minute)(sqsConsumer.consume(handler))

}

class SystemEventProcessor(sqsConsumer: SQSConsumer,
                           orderService: OrderService)
                          (implicit system: ActorSystem, tradingDays: TradingDays)
  extends SQSMessageProcessor(sqsConsumer) {

  import JsonMessageFormats._
  import org.json4s._
  import org.json4s.jackson.JsonMethods._

  private def extractValue(jsonString: String, field: String): Option[JValue] =
    (Try(parse(jsonString)) map { j =>
      parse((j \ "Message").extract[String]) \ field
    }) recoverWith {
      case e => Try(parse(jsonString)) map { _ \ field }
    } toOption

  override def handler: PartialFunction[Message, Future[Unit]] = {

    case m: Message if extractValue(m.getBody, "eventType").exists(_.extract[String] == "CutOffTime") =>

      withCutOffTime { cutoffTime =>
        orderService.processOrdersAtCutoffTime(cutoffTime)
      }

    case m: Message if extractValue(m.getBody, "eventType").exists(_.extract[String] == "PartialProductReportTime") =>

      logger.info("PartialProductReportTime")

      withCutOffTime { cutoffTime =>
        orderService.notifyPendingOrders(cutoffTime)
      }

  }

  private def withCutOffTime(f:ZonedDateTime => Future[Unit]): Future[Unit] = {
    val cutoffTime = tradingDays.getTableTopCutOffTimeFor(tradingDays.getNextWorkingDay())
    f(cutoffTime)
  }

}

