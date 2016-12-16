package com.officeserve.reportservice.actors


import akka.actor.{Actor, ActorLogging, ActorRef, ActorRefFactory, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.amazonaws.services.sqs.model.Message
import com.officeserve.basketservice.web.OrderMessageRep
import com.officeserve.reportservice.actors.SqsActor.{SqsReceive, SqsSend, SqsSent}
import com.officeserve.reportservice.models._
import com.officeserve.reportservice.services.MessageConsumerService.ConsumerResult
import com.officeserve.reportservice.services.{MessageConsumerService, MessageSchedulerService, MessageTranslatorService}
import com.officeserve.reportservice.settings.AppSettings

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}


class SqsConsumer(settings: AppSettings,
                  messageSchedulerService: MessageSchedulerService,
                  messageTranslatorService: MessageTranslatorService,
                  messageConsumerService: MessageConsumerService)(implicit ec: ExecutionContext) extends Actor with ActorLogging {

  import SqsConsumer._

  private val sqsActor = context.parent //It's actually pointing the IdempotentConsumer proxy

  private implicit val timeout = Timeout(settings.sqsSettings.writeTimeout)

  messageSchedulerService.schedule(settings.sqsSettings.fetchingInterval, sqsActor, SqsReceive)

  override def receive: Receive = {
    case SqsMessage(message) =>
      val logPrefix = s"Message [${message.getMessageId}] - "

      log.debug(s"$logPrefix processing")

      messageTranslatorService.fromMessageBodyToEvent(message.getBody) match {
        case Success(Event(PurchaseComplete, entities)) =>
          generateSubmessage(Event(StoreOrders, entities), sqsActor, message, withAck = true)
          generateSubmessage(Event(SendOrderDocuments, entities), sqsActor, message)

        case Success(Event(SendOrderDocuments, entities)) =>
          messageConsumerService.sendOrderDocuments(entities).onComplete {
            responseHandler(logPrefix + "SendOrderDocuments - ", message)
          }

        case Success(Event(PurchaseCancelled, entities)) =>
          generateSubmessage(Event(StoreOrders, entities), sqsActor, message, withAck = true)
          generateSubmessage(Event(SendCancellationDocuments, entities), sqsActor, message)

        case Success(Event(SendCancellationDocuments, entities)) =>
          messageConsumerService.sendCancellation(entities).onComplete {
            responseHandler(logPrefix + "SendCancellationDocuments - ", message)
          }

        case Success(Event(StoreOrders, entities)) =>
          messageConsumerService.storeOrders(entities).onComplete {
            responseHandler(logPrefix + "StoreOrders - ", message)
          }

        case Success(Event(CutOffTimeProcessOrders, entities)) =>
          messageConsumerService.sendDeliveryNotes(entities).onComplete {
            responseHandler(logPrefix + "CutOffProcessOrders - ", message)
          }

        case Success(Event(PartialProductReportTime, _)) =>
          messageConsumerService.sendProductReport.onComplete {
            responseHandler(logPrefix + "PartialProductReportTime - ", message)
          }

        case Success(Event(CutOffTime, _)) =>
          messageConsumerService.sendProductReport.onComplete {
            responseHandler(logPrefix + "CutOffTime - ", message)
          }

        case Success(event) =>
          log.error(s"$logPrefix Message not yet handled.\n\n$event\n")
        case Failure(e) =>
          log.error(e, s"$logPrefix Cannot parse message. Skipping it.\n\n$message\n")

      }

    case SqsAck(message) =>
      log.debug(s"Message [${message.getMessageId}] Sending ack")
      sqsActor forward SqsAck(message)
  }

  private def responseHandler[T](logPrefix: String, message: Message): PartialFunction[Try[ConsumerResult[T]], Unit] = {
    case Success(result) if result.failed.isEmpty =>
      log.debug(s"$logPrefix Operation succeeded. Removing the message from SQS")
      sqsActor ! SqsAck(message)
    case Success(result) if result.succeeded.isEmpty =>
      log.error(s"$logPrefix Operation failed with ${result.failed.size} failures. All entities failed, keeping the message in SQS")
      logFailures(result.failed)
    case Success(result) if result.failed.nonEmpty =>
      log.error(s"$logPrefix Operation partially succeeded with ${result.failed.size} failures. Removing the message from SQS")
      logFailures(result.failed)
      sqsActor ! SqsAck(message)
    case Failure(e) =>
      log.error(e, s"$logPrefix Error during processing. Keeping the message in SQS")

  }

  private def logFailures[T](failed: Set[(T, Throwable)]) =
    failed.foreach { entry =>
      log.error(entry._2, "Failed entity: \n" + entry._1.toString)
    }

  private def generateSubmessage(event: Event[EventType, OrderMessageRep], recipient: ActorRef, originalMessage: Message, withAck: Boolean = false): Unit = {
    (recipient ? SqsSend(messageTranslatorService.fromEventToMessageBody(event)))
      .mapTo[SqsSent]
      .onComplete {
        case Success(sent) =>
          log.debug(s"Message [${sent.messageId}] containing event ${event.eventType} stored.${if (withAck) s" Sending Ack for message [${originalMessage.getMessageId}]" else ""}")
          if (withAck) {
            recipient ! SqsAck(originalMessage)
          }
        case Failure(e) =>
          val errorMsg = if (withAck) " Leaving the original in SQS." else ""
          log.error(e, s"Error storing submessage $event for original message [${originalMessage.getMessageId}].$errorMsg")
      }
  }

}

object SqsConsumer {
  val name = "sqs-consumer"

  def factory(settings: AppSettings,
              messageSchedulerService: MessageSchedulerService,
              messageTranslatorService: MessageTranslatorService,
              messageConsumerService: MessageConsumerService,
              name: String = name)
             (implicit ec: ExecutionContext): (ActorRefFactory => ActorRef) =
    _.actorOf(props(settings, messageSchedulerService, messageTranslatorService, messageConsumerService), name)

  def props(settings: AppSettings,
            messageSchedulerService: MessageSchedulerService,
            messageTranslatorService: MessageTranslatorService,
            messageConsumerService: MessageConsumerService)
           (implicit ex: ExecutionContext): Props = {
    Props(new SqsConsumer(settings, messageSchedulerService, messageTranslatorService, messageConsumerService))
  }

  case class SqsMessage(message: Message)

  case class SqsAck(message: Message)

}