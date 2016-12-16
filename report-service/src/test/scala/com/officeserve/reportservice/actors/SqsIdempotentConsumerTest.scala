package com.officeserve.reportservice.actors

import akka.actor.{ActorRef, ActorRefFactory, ActorSystem}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import com.amazonaws.services.sqs.model.Message
import com.officeserve.reportservice.actors.SqsActor.{SqsDelete, SqsReceive}
import com.officeserve.reportservice.actors.SqsConsumer.{SqsAck, SqsMessage}
import com.officeserve.reportservice.services.MessageService
import com.officeserve.reportservice.settings.AppSettings
import com.redis.RedisConnectionException
import com.typesafe.config.ConfigFactory
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterAll, FunSpecLike, Matchers}

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future


class SqsIdempotentConsumerTest extends TestKit(ActorSystem("testSystem")) with ImplicitSender
  with FunSpecLike with BeforeAndAfterAll with Matchers with MockitoSugar with SqsIdempotentConsumerFixtures {

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  describe("SqsIdempotentConsumer") {
    val settings = new AppSettings(ConfigFactory.load())


    describe("when receiving a message") {
      describe("in any case") {
        val messageService = mock[MessageService]
        when(messageService.pending(message.getMessageId)) thenReturn Future.successful(true)
        val (sqsIdempotentConsumer, sqsConsumerProbe) = createActor(settings, messageService, 5)
        val m = SqsMessage(message)

        it("should try mark the message status as PENDING") {
          sqsIdempotentConsumer ! m
          expectNoMsg()
          verify(messageService).pending(message.getMessageId)
        }
      }
      describe("never received before") {
        val messageService = mock[MessageService]
        when(messageService.pending(message.getMessageId)) thenReturn Future.successful(true)
        val (sqsIdempotentConsumer, sqsConsumerProbe) = createActor(settings, messageService, 1)
        val m = SqsMessage(message)

        it("should not reply anything to the sender") {
          sqsIdempotentConsumer ! m
          expectNoMsg()
        }
        it("should send it to the sqsConsumer actor") {
          sqsIdempotentConsumer ! m
          sqsConsumerProbe.expectMsg(m)

        }
      }
      describe("previously received") {
        val messageService = mock[MessageService]
        when(messageService.pending(message.getMessageId)) thenReturn Future.successful(false)
        val (sqsIdempotentConsumer, sqsConsumerProbe) = createActor(settings, messageService, 2)
        val m = SqsMessage(message)

        it("should not reply to the sender") {
          sqsIdempotentConsumer ! m
          expectNoMsg()
        }
        it("should not send it to the sqsConsumer actor") {
          sqsConsumerProbe.expectNoMsg()
        }
      }
      describe("and redis is down") {
        val messageService = mock[MessageService]
        when(messageService.pending(message.getMessageId)) thenReturn Future.failed(RedisConnectionException("boo"))
        val (sqsIdempotentConsumer, sqsConsumerProbe) = createActor(settings, messageService, 3)
        val m = SqsMessage(message)

        it("should not send it to the sqsConsumer actor") {
          sqsIdempotentConsumer ! m
          sqsConsumerProbe.expectNoMsg()
        }
      }
    }

    describe("when receiving an Ack for a message") {
      describe("in any case") {
        val messageService = mock[MessageService]
        when(messageService.processed(message.getMessageId)) thenReturn Future.successful(true)
        val (sqsIdempotentConsumer, sqsConsumerProbe) = createActor(settings, messageService, 4)
        val m = SqsAck(message)

        it("should try to mark the message status as PROCESSED") {
          sqsIdempotentConsumer ! m
          expectNoMsg()
          verify(messageService).processed(message.getMessageId)
        }
        it("should not reply to the sender") {
          sqsIdempotentConsumer ! m
          expectNoMsg()
        }

      }
      describe("and it managed to mark the message as PROCESSED successfully") {
        val messageService = mock[MessageService]
        when(messageService.processed(message.getMessageId)) thenReturn Future.successful(true)
        val (sqsIdempotentConsumer, sqsConsumerProbe, sqsParentProbe) = createActorWithTestActorRef(settings, messageService, 5)
        val m = SqsAck(message)
        it("should send an SqsDelete to sqsActor") {
          sqsIdempotentConsumer ! m
          sqsParentProbe.expectMsg(SqsDelete(message))
        }
      }
      describe("and it failed marking the message as PROCESSED") {
        val messageService = mock[MessageService]
        when(messageService.processed(message.getMessageId)) thenReturn Future.successful(false)
        val (sqsIdempotentConsumer, sqsConsumerProbe, sqsParentProbe) = createActorWithTestActorRef(settings, messageService, 5)
        val m = SqsAck(message)
        it("should send an SqsDelete to sqsActor anyway") {
          sqsIdempotentConsumer ! m
          sqsParentProbe.expectMsg(SqsDelete(message))
        }
      }
      describe("and redis is down") {
        val messageService = mock[MessageService]
        when(messageService.processed(message.getMessageId)) thenReturn Future.failed(RedisConnectionException("boo"))
        val (sqsIdempotentConsumer, sqsConsumerProbe, sqsParentProbe) = createActorWithTestActorRef(settings, messageService, 5)
        val m = SqsAck(message)
        it("should send an SqsDelete to sqsActor anyway") {
          sqsIdempotentConsumer ! m
          sqsParentProbe.expectMsg(SqsDelete(message))
        }
      }
    }

    describe("when receiving an SqsReceive") {
      val messageService = mock[MessageService]
      when(messageService.processed(message.getMessageId)) thenReturn Future.failed(RedisConnectionException("boo"))
      val (sqsIdempotentConsumer, sqsConsumerProbe, sqsParentProbe) = createActorWithTestActorRef(settings, messageService, 5)
      val m = SqsReceive
      it("should send it to the parent") {
        sqsIdempotentConsumer ! m
        sqsParentProbe.expectMsg(m)
      }
    }
  }


  def createActor(settings: AppSettings, messageService: MessageService, actorNumber: Int): (ActorRef, TestProbe) = {

    // See here http://christopher-batey.blogspot.co.uk/2014/02/akka-testing-messages-sent-to-child.html
    val sqsConsumerProbe = TestProbe()
    val sqsConsumerFactory: (ActorRefFactory) => ActorRef = _ => sqsConsumerProbe.ref

    val factory = SqsIdempotentConsumer.factory(messageService, settings, sqsConsumerFactory, SqsConsumer.name + "-" + actorNumber)
    (factory(system), sqsConsumerProbe)
  }

  def createActorWithTestActorRef(settings: AppSettings, messageService: MessageService, actorNumber: Int): (ActorRef, TestProbe, TestProbe) = {
    val sqsConsumerProbe = TestProbe()
    val parentProbe = TestProbe()
    val actor = TestActorRef(SqsIdempotentConsumer.probe(messageService, settings, _ => sqsConsumerProbe.ref), parentProbe.ref, SqsConsumer.name + "-" + actorNumber)

    (actor, sqsConsumerProbe, parentProbe)
  }


}

trait SqsIdempotentConsumerFixtures {
  val message = new Message().withMessageId("testId1").withReceiptHandle("testReceiptHandle1").withBody("testBody1")
}