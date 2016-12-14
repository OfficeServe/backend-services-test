package com.officeserve.basketservice.clients

import akka.actor.{Actor, ActorLogging, Props}
import akka.pattern.pipe
import com.amazonaws.services.sns.model.PublishResult
import com.github.dwhjames.awswrap.sns.AmazonSNSScalaClient
import com.officeserve.basketservice.settings.NotificationsSettings
import com.officeserve.basketservice.web.OrderMessageRep
import com.typesafe.scalalogging.StrictLogging
import org.json4s._
import org.json4s.jackson.Serialization

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps
import scalaz.\/

class SNSPublisher(snsClient: AmazonSNSScalaClient) extends StrictLogging {

  def publish[T](topic: String, message: EventMessage[T])
                (implicit ec: ExecutionContext, formats: Formats): Future[PublishResultError \/ PublishResult] = {
    snsClient.publish(topic, Serialization.write(message)) map {
      \/ right
    } recover {
      case e =>
        logger.error(s"Failed while sending message [$message].", e)
        \/ left GenericError(e.getMessage)
    }
  }

}

case class EventMessage[T](eventType: EventType, entities: Seq[T])

sealed trait EventType

case object PurchaseComplete                      extends EventType
case object PurchaseCancelled                     extends EventType
case object CutOffTimeProcessOrders               extends EventType
case object PartialProductReportTimeWithOrders extends EventType

sealed abstract class PublishResultError(message: String) {
  override def toString = message
}

case class GenericError(message: String) extends PublishResultError(message)

//=======================================================================================

class SNSPublisherActor(snsSender: SNSPublisher, notificationsSettings: NotificationsSettings)
  extends Actor with ActorLogging {

  import JsonMessageFormats._
  import SNSPublisherActor._
  import context.dispatcher

  override def receive: Receive = {
    case SendPurchaseCompleteNotification(messages) =>
      snsSender.publish(notificationsSettings.ordersCompleteTopicArn,
        EventMessage(PurchaseComplete, messages)) pipeTo sender
    case SendPurchaseCancelledNotification(messages) =>
      snsSender.publish(notificationsSettings.ordersCompleteTopicArn,
        EventMessage(PurchaseCancelled, messages)) pipeTo sender
    case CutoffTimeNotification(messages) =>
      snsSender.publish(notificationsSettings.ordersCompleteTopicArn,
        EventMessage(CutOffTimeProcessOrders, messages)) pipeTo sender
    case PartialProductReportTimeNotification(messages) =>
      snsSender.publish(notificationsSettings.ordersCompleteTopicArn,
        EventMessage(PartialProductReportTimeWithOrders, messages)) pipeTo sender
  }

}

object SNSPublisherActor {

  case class SendPurchaseCompleteNotification(messages: Seq[OrderMessageRep])

  object SendPurchaseCompleteNotification {
    def apply(message: OrderMessageRep): SendPurchaseCompleteNotification = SendPurchaseCompleteNotification(Seq(message))
  }

  case class SendPurchaseCancelledNotification(messages: Seq[OrderMessageRep])

  case class CutoffTimeNotification(messages: Seq[OrderMessageRep])

  case class PartialProductReportTimeNotification(messages: Seq[OrderMessageRep])

  object SendPurchaseCancelledNotification {
    def apply(message: OrderMessageRep): SendPurchaseCancelledNotification = SendPurchaseCancelledNotification(Seq(message))
  }

  def props(snsSender: SNSPublisher, notificationsSettings: NotificationsSettings) =
    Props(new SNSPublisherActor(snsSender, notificationsSettings))

}
