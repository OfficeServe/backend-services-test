package com.officeserve.reportservice.actors

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.TestActor.AutoPilot
import akka.testkit.{ImplicitSender, TestActor, TestActorRef, TestKit, TestProbe}
import com.amazonaws.services.sqs.model.Message
import com.officeserve.basketservice.web.OrderMessageRep
import com.officeserve.reportservice.actors.SqsActor.{SqsSend, SqsSent}
import com.officeserve.reportservice.actors.SqsConsumer.{SqsAck, SqsMessage}
import com.officeserve.reportservice.models._
import com.officeserve.reportservice.services.MessageConsumerService.ConsumerResult
import com.officeserve.reportservice.services.MessageConsumerService.Exceptions.UnexpectedPaymentTypeException
import com.officeserve.reportservice.services.{MessageConsumerService, MessageSchedulerService, MessageTranslatorService}
import com.officeserve.reportservice.settings.AppSettings
import com.typesafe.config.ConfigFactory
import org.mockito.ArgumentMatchers
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterAll, FunSpecLike, Matchers}
import org.mockito.Mockito._
import org.mockito.ArgumentMatchers._
import org.mockito.stubbing.OngoingStubbing

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future
import scala.util.Success

class SqsConsumerTest extends TestKit(ActorSystem("testSystem")) with ImplicitSender
  with FunSpecLike with BeforeAndAfterAll with Matchers with MockitoSugar with SqsConsumerFixtures {

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  describe("SqsConsumer") {
    val settings = new AppSettings(ConfigFactory.load())
    val messageSchedulerService = mock[MessageSchedulerService]


    describe("when receiving a SqsAck") {
      val messageTranslatorService = mock[MessageTranslatorService]
      val messageConsumerService = mock[MessageConsumerService]
      val (sqsConsumer, parentProbe) = createActorWithTestActorRef(settings, messageSchedulerService, messageTranslatorService, messageConsumerService, 1)
      it("should forward it to the parent") {
        sqsConsumer ! SqsAck(message)
        parentProbe.expectMsg(SqsAck(message))
      }
      it("should not reply to the sender") {
        sqsConsumer ! SqsAck(message)
        expectNoMsg()
      }
    }

    describe("when receiving a SqsMessage") {

      describe("regardless of the message content") {
        val sqsMessage = mock[Message]
        when(sqsMessage.getBody) thenReturn "testMessageBody"
        when(sqsMessage.getMessageId) thenReturn "testMessageId"

        val messageTranslatorService = mock[MessageTranslatorService]
        val messageConsumerService = mock[MessageConsumerService]
        val (sqsConsumer, parentProbe) = createActorWithTestActorRef(settings, messageSchedulerService, messageTranslatorService, messageConsumerService, 2)
        it("should call the MessageTranslator to convert the SQS Message into an Event") {
          sqsConsumer ! SqsMessage(sqsMessage)

          verify(messageTranslatorService, times(1)).fromMessageBodyToEvent(anyString)
          verify(sqsMessage, times(1)).getBody

        }
      }
      describe("containing a PurchaseComplete event") {
        testSubmessages(settings, messageSchedulerService, PurchaseComplete, 3)(StoreOrders, SendOrderDocuments)
      }
      describe("containing a PurchaseCancelled event") {
        testSubmessages(settings, messageSchedulerService, PurchaseCancelled, 4)(StoreOrders, SendCancellationDocuments)
      }

      describe("containing a SendOrderDocument") {
        testGenericResponseHandling(SendOrderDocuments, settings, messageSchedulerService, 5) {
          (messageConsumerService, result) => when(messageConsumerService.sendOrderDocuments(any[Set[OrderMessageRep]])) thenReturn result
        }
      }
      describe("containing a SendCancellationDocuments") {
        testGenericResponseHandling(SendCancellationDocuments, settings, messageSchedulerService, 6) {
          (messageConsumerService, result) => when(messageConsumerService.sendCancellation(any[Set[OrderMessageRep]])) thenReturn result
        }
      }
      describe("containing a CutOffProcessOrders") {
        testGenericResponseHandling(CutOffTimeProcessOrders, settings, messageSchedulerService, 7) {
          (messageConsumerService, result) => when(messageConsumerService.sendDeliveryNotes(any[Set[OrderMessageRep]])) thenReturn result
        }
      }
      describe("containing a StoreOrders") {
        testGenericResponseHandling(StoreOrders, settings, messageSchedulerService, 8) {
          (messageConsumerService, result) => when(messageConsumerService.storeOrders(any[Set[OrderMessageRep]])) thenReturn result
        }
      }
      describe("containing a PartialProductReportTime") {
        testGenericResponseHandling(PartialProductReportTime, settings, messageSchedulerService, 9) {
          (messageConsumerService, result) => when(messageConsumerService.sendProductReport) thenReturn result
        }
      }
      describe("containing a CutOffTime") {
        testGenericResponseHandling(CutOffTime, settings, messageSchedulerService, 10) {
          (messageConsumerService, result) => when(messageConsumerService.sendProductReport) thenReturn result
        }
      }
    }

  }

  def testSubmessages(settings: AppSettings, messageSchedulerService: MessageSchedulerService, incomingEventType: EventType, actorNumber: Int)(expectedEventTypes: EventType*) = {
    val messageTranslatorService = mock[MessageTranslatorService]
    val messageConsumerService = mock[MessageConsumerService]

    val orders = Set[OrderMessageRep]()

    val incomingEvent: Event[EventType, OrderMessageRep] = Event(incomingEventType, orders)

    val expectedMessages = for {
      eventType <- expectedEventTypes
      expectedEvent: Event[EventType, OrderMessageRep] = Event(eventType, orders)
      expectedEventMessageBody = s"${eventType}MessageBody"
      _ = when(messageTranslatorService.fromEventToMessageBody(ArgumentMatchers.eq(expectedEvent))) thenReturn expectedEventMessageBody
      expectedMessage = SqsSend(expectedEventMessageBody)
    } yield expectedMessage


    val sqsMessage = mock[Message]
    when(sqsMessage.getBody) thenReturn s"${incomingEventType}MessageBody"
    when(sqsMessage.getMessageId) thenReturn s"${incomingEventType}MessageId"


    when(messageTranslatorService.fromMessageBodyToEvent(anyString)) thenReturn Success(incomingEvent)


    val (sqsConsumer, parentProbe) = createActorWithTestActorRef(settings, messageSchedulerService, messageTranslatorService, messageConsumerService, actorNumber)

    val expectedEventsString = expectedEventTypes.foldLeft("")((acc, curr) => s"${acc} SqsSend($curr)")

    it(s"should send $expectedEventsString messages to the parent and acknowledge the original message") {

      parentProbe.setAutoPilot(new TestActor.AutoPilot {
        override def run(sender: ActorRef, msg: Any): AutoPilot = msg match {
          case SqsSend(messageBody) => sender ! SqsSent(s"messageId for $messageBody"); TestActor.KeepRunning
          case SqsAck(_) => TestActor.KeepRunning
        }
      })

      sqsConsumer ! SqsMessage(sqsMessage)

      parentProbe.expectMsgAllOf(
        expectedMessages :+ SqsAck(sqsMessage):_*
      )

    }
  }

  def testGenericResponseHandling(eventType: EventType, settings: AppSettings, messageSchedulerService: MessageSchedulerService, actorNumber: Int)(f: (MessageConsumerService, Future[ConsumerResult[OrderMessageRep]]) => OngoingStubbing[Future[ConsumerResult[OrderMessageRep]]]): Unit = {
    val messageTranslatorService = mock[MessageTranslatorService]

    val order1 = mock[OrderMessageRep]
    val order2 = mock[OrderMessageRep]
    val orders = Set[OrderMessageRep](order1, order2)

    val event: Event[EventType, OrderMessageRep] = Event(eventType, orders)

    val sqsMessage = mock[Message]
    when(sqsMessage.getBody) thenReturn s"${eventType}MessageBody"
    when(sqsMessage.getMessageId) thenReturn s"${eventType}MessageId"

    when(messageTranslatorService.fromMessageBodyToEvent(anyString)) thenReturn Success(event)

    describe("and there where no failures") {
      val messageConsumerService = mock[MessageConsumerService]

      val fullSuccess = Future.successful(ConsumerResult(orders, Set[(OrderMessageRep, Throwable)]()))

      f(messageConsumerService, fullSuccess)

      val (sqsConsumer, parentProbe) = createActorWithTestActorRef(settings, messageSchedulerService, messageTranslatorService, messageConsumerService, actorNumber * 100 + 1)

      it("should acknowledge the original message") {
        sqsConsumer ! SqsMessage(sqsMessage)
        parentProbe.expectMsg(SqsAck(sqsMessage))
      }
    }
    describe("and there where some failures") {
      val messageConsumerService = mock[MessageConsumerService]

      val partialSuccess = Future.successful(ConsumerResult(
        succeeded = Set(order2),
        failed = Set[(OrderMessageRep, Throwable)]((order1, UnexpectedPaymentTypeException(order1)))
      ))

      f(messageConsumerService, partialSuccess)

      val (sqsConsumer, parentProbe) = createActorWithTestActorRef(settings, messageSchedulerService, messageTranslatorService, messageConsumerService, actorNumber * 100 + 2)

      it("should acknowledge the original message") {
        sqsConsumer ! SqsMessage(sqsMessage)
        parentProbe.expectMsg(SqsAck(sqsMessage))
      }
    }
    describe("and there where just failures") {
      val messageConsumerService = mock[MessageConsumerService]

      val partialSuccess = Future.successful(ConsumerResult(
        succeeded = Set(),
        failed = Set[(OrderMessageRep, Throwable)](
          (order1, UnexpectedPaymentTypeException(order1)),
          (order2, UnexpectedPaymentTypeException(order2))
        )
      ))

      f(messageConsumerService, partialSuccess)

      val (sqsConsumer, parentProbe) = createActorWithTestActorRef(settings, messageSchedulerService, messageTranslatorService, messageConsumerService, actorNumber * 100 + 3)


      it("should NOT acknowledge the original message") {
        sqsConsumer ! SqsMessage(sqsMessage)
        parentProbe.expectNoMsg()
      }
    }
    describe("and the whole request fails") {
      val messageConsumerService = mock[MessageConsumerService]
      f(messageConsumerService, Future.failed(new Exception()))

      val (sqsConsumer, parentProbe) = createActorWithTestActorRef(settings, messageSchedulerService, messageTranslatorService, messageConsumerService, actorNumber * 100 + 4)

      it("should NOT acknowledge the original message") {
        sqsConsumer ! SqsMessage(sqsMessage)
        parentProbe.expectNoMsg()
      }
    }
  }

  def createActor(settings: AppSettings, messageSchedulerService: MessageSchedulerService, messageTranslatorService: MessageTranslatorService, messageConsumerService: MessageConsumerService, actorNumber: Int): ActorRef = {
    val factory = SqsConsumer.factory(settings, messageSchedulerService, messageTranslatorService, messageConsumerService, SqsConsumer.name + "-" + actorNumber)
    factory(system)
  }

  def createActorWithTestActorRef(settings: AppSettings, messageSchedulerService: MessageSchedulerService, messageTranslatorService: MessageTranslatorService, messageConsumerService: MessageConsumerService, actorNumber: Int): (ActorRef, TestProbe) = {
    val sqsParent = TestProbe()
    val actor = TestActorRef(SqsConsumer.props(settings, messageSchedulerService, messageTranslatorService, messageConsumerService), sqsParent.ref, SqsConsumer.name + "-" + actorNumber)
    (actor, sqsParent)
  }
}

trait SqsConsumerFixtures {
  val message = new Message().withMessageId("testId1").withReceiptHandle("testReceiptHandle1").withBody("testBody1")
}