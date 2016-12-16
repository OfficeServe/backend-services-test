package com.officeserve.reportservice.actors

import akka.actor.{ActorRef, ActorRefFactory, ActorSystem}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import com.amazonaws.services.sqs.AmazonSQSClient
import com.amazonaws.services.sqs.model._
import com.officeserve.reportservice.actors.SqsActor.{SqsDelete, SqsReceive, SqsSend, SqsSent}
import com.officeserve.reportservice.actors.SqsConsumer.SqsMessage
import com.officeserve.reportservice.settings.AppSettings
import com.typesafe.config.ConfigFactory
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.mockito.{ArgumentCaptor, ArgumentMatchers}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterAll, FunSpecLike, Matchers}

import scala.concurrent.ExecutionContext.Implicits._

class SqsActorTest extends TestKit(ActorSystem("testSystem")) with ImplicitSender
  with FunSpecLike with BeforeAndAfterAll with Matchers with MockitoSugar with SqsActorFixtures {

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  describe("SqsActor") {

    val settings = new AppSettings(ConfigFactory.load())

    describe("when told to receive messages") {
      val sqsClient = mock[AmazonSQSClient]
      val sqsActor = createSqsActor(settings, sqsClient, 1)
      when(sqsClient.receiveMessage(any[ReceiveMessageRequest])).thenReturn(receiveMessageResult)

      it("should send back the retrieved messages") {
        sqsActor ! SqsReceive
        expectMsgAllOf(SqsMessage(message1), SqsMessage(message2))
      }
    }

    describe("when told to delete a message") {
      val sqsClient = mock[AmazonSQSClient]
      val sqsActor = createSqsActor(settings, sqsClient, 2)
      it("should send a delete message to SQS") {
        sqsActor ! SqsDelete(message1)
        expectNoMsg()

        verify(sqsClient, times(1)).deleteMessage(settings.sqsSettings.queueUrl.toString, message1.getReceiptHandle)
      }
    }

    describe("when told to send a messages") {
      val sqsClient = mock[AmazonSQSClient]
      when(sqsClient.sendMessage(anyString(), anyString())) thenReturn new SendMessageResult().withMessageId("messageId")
      val sqsActor = createSqsActor(settings, sqsClient, 3)
      it("should send the message to SQS and should reply to the sender with SqsSent(messageId)") {
        sqsActor ! SqsSend("message1")

        expectMsg(SqsSent("messageId"))

        val urlCaptor = ArgumentCaptor.forClass(classOf[String])

        verify(sqsClient, times(1)).sendMessage(urlCaptor.capture(), ArgumentMatchers.eq("message1"))
        urlCaptor.getValue shouldBe settings.sqsSettings.queueUrl.toString
      }
    }
  }

  def createSqsActor(settings: AppSettings, sqsClient: AmazonSQSClient, actorNumber: Int): ActorRef = {

    // See here http://christopher-batey.blogspot.co.uk/2014/02/akka-testing-messages-sent-to-child.html
    val probe = TestProbe()
    val factory: (ActorRefFactory => ActorRef) = _ => probe.ref

    val sqsActorFactory = SqsActor.factory(sqsClient, settings, factory, SqsActor.name + "-" + actorNumber)
    sqsActorFactory(system)
  }
}

trait SqsActorFixtures {
  val message1 = new Message().withMessageId("testId1").withReceiptHandle("testReceiptHandle1").withBody("testBody1")
  val message2 = new Message().withMessageId("testId2").withReceiptHandle("testReceiptHandle2").withBody("testBody2")
  val receiveMessageResult = new ReceiveMessageResult()
    .withMessages(message1)
    .withMessages(message2)

}
