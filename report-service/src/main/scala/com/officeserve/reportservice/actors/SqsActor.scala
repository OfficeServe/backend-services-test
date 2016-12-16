package com.officeserve.reportservice.actors

import akka.actor.{Actor, ActorLogging, ActorRef, ActorRefFactory, Props}
import com.amazonaws.services.sqs.AmazonSQSClient
import com.amazonaws.services.sqs.model._
import com.officeserve.reportservice.settings.AppSettings

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext

class SqsActor(sqsClient: AmazonSQSClient, //TODO: either use AmazonSQSAsyncClient or wrap it in scala Future
               settings: AppSettings,
               idempotentConsumerFactory: (ActorRefFactory) => ActorRef
              )
              (implicit ec: ExecutionContext) extends Actor with ActorLogging {

  import SqsActor._
  import SqsConsumer._

  private val sqsIdempotentConsumer = idempotentConsumerFactory(context)


  private val queueUrl = settings.sqsSettings.queueUrl.toString

  private val request = new ReceiveMessageRequest(queueUrl).withMaxNumberOfMessages(settings.sqsSettings.maxMessages)

  override def receive: Receive = {
    case SqsReceive =>
      log.debug("Receiving messages")
      sqsClient.receiveMessage(request).getMessages.asScala.foreach { msg =>
        sender() ! SqsMessage(msg)
      }

    case SqsDelete(message) =>
      sqsClient.deleteMessage(queueUrl, message.getReceiptHandle)
      log.debug(s"Message [${message.getMessageId}] Deleted")

    case SqsSend(message) =>
      val result = sqsClient.sendMessage(queueUrl, message)
      sender() ! SqsSent(result.getMessageId)
      log.debug(s"Message [${result.getMessageId}] Sent")
  }
}

object SqsActor {

  val name = "sqs-actor"

  def factory(sqsClient: AmazonSQSClient,
              settings: AppSettings,
              idempotentConsumerFactory: (ActorRefFactory) => ActorRef,
              name: String = name
             )
             (implicit ex: ExecutionContext): (ActorRefFactory) => ActorRef =
    _.actorOf(Props(new SqsActor(sqsClient, settings, idempotentConsumerFactory)), name)

  case object SqsReceive

  case class SqsDelete(message: Message)

  case class SqsSend(messageBody: String)

  case class SqsSent(messageId: String)

}
