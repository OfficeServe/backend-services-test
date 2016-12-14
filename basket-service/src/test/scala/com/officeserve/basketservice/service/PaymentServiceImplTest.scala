package com.officeserve.basketservice.service

import java.time.ZonedDateTime
import java.util.UUID

import akka.actor.ActorSystem
import com.amazonaws.services.sns.model.PublishResult
import com.github.dwhjames.awswrap.sns.AmazonSNSScalaClient
import com.officeserve.basketservice.clients._
import com.officeserve.basketservice.persistence._
import com.officeserve.basketservice.settings.{BasketConfig, NotificationsSettings}
import com.officeserve.basketservice.web.BasketServiceApiPaymentResponseMethodTest.DummyPaymentMethodRepoImpl
import com.officeserve.basketservice.web._
import officeserve.commons.domain.{MoneyDom, UserDom}
import officeserve.commons.spray.webutils.DateSerializerUtil
import org.mockito.Mockito.{reset, times, verify, verifyZeroInteractions, when => mockitoWhen}
import org.mockito.{Matchers => mockMatchers}
import org.scalatest.mock.MockitoSugar
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.{BeforeAndAfter, FeatureSpec, GivenWhenThen, Matchers}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Random
import scalaz.\/

/**
  * Created by mo on 29/07/2016.
  */
class PaymentServiceImplTest extends FeatureSpec with GivenWhenThen with PaymentServiceImplTestFixture with BeforeAndAfter {

  before {
    reset(orderRepository, paymentGateway)
  }

  info("Payment service")
  info("Should process request to process an order respecting all the busness rules")

  feature("Process an already created order for payment") {

    forAll(requiredFieldsBeforePayment) { (testCase, testData) => {
      scenario(testCase) {
        Given("I have an order to pay")
        val order = Order(
          orderId,
          "1234",
          basketDom,
          ZonedDateTime.now(),
          Started,
          testData.invoiceNumber,
          None,
          testData.deliveryDate,
          testData.deliverySlot,
          testData.deliveryAddress,
          testData.invoiceAddress,
          testData.telephoneNum,
          Some(PaymentResponse(Succeeded,
            testData.transactionId,
            testData.gatewayTransactionId,
            testData.paymentMethod.get)),
          NoTableware(0)
        )
        When("PaymentService processPayment is invoked ")
        mockitoWhen(orderRepository.getOrder(mockMatchers.any[String])).thenReturn(Future(Some(order)))

        Then("it should throw an InvalidRequestException for missing required fields")
        intercept[InvalidRequestException] {
          Await.result(paymentService.processPayment(orderId, userId, paymentRequest, paymentMethodService), 2 seconds)
        }
      }
    }
    }
  }

  feature("Failed or Succeeded Payment") {

    scenario("failed payment") {
      Given("I have an order to pay")

      When("PaymentService processPayment is invoked ")
      mockitoWhen(orderRepository.getOrder(mockMatchers.any[String])).thenReturn(Future.successful(Some(readyOrder)))
      mockitoWhen(orderRepository.updateOrders(mockMatchers.anyObject[Order]())).thenReturn(Future.successful(()))
      mockitoWhen(dateUtils.now).thenReturn(nowDateBeforeCutOffTIme)

      And("Spreedly payment gateway is invoked")
      mockitoWhen(paymentGateway.pay(mockMatchers.any[SpreedlyRequest])).thenReturn(Future.successful(failedPaymentResp))

      Then("it should throw an PaymentFailedException for failed payment")
      val purchaseComplete = Await.result(paymentService.processPayment(orderId, userId, paymentRequest, paymentMethodService), futureWaitDuration)
      purchaseComplete.order.paymentFailed shouldBe true

      And("Send payment gateway client should be invoked")
      verify(paymentGateway).pay(mockMatchers.any[SpreedlyRequest])

      And("No notification should be sent")
      verifyZeroInteractions(sNSSender)

    }

    scenario("successful payment") {
      Given("I have an order to pay")

      When("PaymentService processPayment is invoked ")
      mockitoWhen(orderRepository.getOrder(mockMatchers.any[String])).thenReturn(Future.successful(Some(readyOrder)))
      mockitoWhen(orderRepository.updateOrders(mockMatchers.anyObject[Order]())).thenReturn(Future.successful(()))
      mockitoWhen(sNSSender.publish(mockMatchers.anyObject(), mockMatchers.anyObject())(mockMatchers.anyObject(), mockMatchers.anyObject()))
        .thenReturn(Future.successful(\/ right new PublishResult().withMessageId("MyId")))
      mockitoWhen(dateUtils.now).thenReturn(nowDateBeforeCutOffTIme)

      And("Spreedly payment gateway is invoked")
      mockitoWhen(paymentGateway.pay(mockMatchers.any[SpreedlyRequest])).thenReturn(Future.successful(successfulPaymentResp))

      Then("it should return a purchaseComplete object")
      val response = Await.result(paymentService.processPayment(orderId, userId, paymentRequest,paymentMethodService), futureWaitDuration)
      purchaseCompleteEquals(response, expectedPurchaseComplete())

      And("Send Notification client should be invoked")
      verify(sNSSender).publish(mockMatchers.anyObject(), mockMatchers.anyObject())(mockMatchers.anyObject(), mockMatchers.anyObject())
    }

    scenario("request to process an already succeed order") {
      Given("I have an order already processed")

      When("PaymentService processPayment is invoked ")
      mockitoWhen(orderRepository.getOrder(mockMatchers.any[String])).thenReturn(Future.successful(Some(alreadyProcessedOrder)))
      mockitoWhen(orderRepository.getInvoiceNumber(mockMatchers.any[Order])).thenReturn(Future.successful(13))

      And("Spreedly payment gateway is invoked")
      mockitoWhen(paymentGateway.pay(mockMatchers.any[SpreedlyRequest])).thenReturn(Future.successful(successfulPaymentResp))

      Then("it should return a purchaseComplete object with the original")

      val response = Await.result(paymentService.processPayment(orderId, userId, paymentRequest, paymentMethodService), futureWaitDuration)
      purchaseCompleteEquals(response, expectedPurchaseComplete(alreadyProcessedOrder))

      And("No payment should be attempted")
      verifyZeroInteractions(paymentGateway)

      And("invoice number should not set")
      verify(orderRepository, times(0)).getInvoiceNumber(mockMatchers.any[Order])

      And("No notification should be sent")
      verifyZeroInteractions(sNSSender)
    }

    scenario("payment with bad deliveryDate") {
      Given("I have an order to pay")

      When("PaymentService processPayment is invoked ")
      mockitoWhen(orderRepository.getOrder(mockMatchers.any[String])).thenReturn(Future(Some(readyOrder)))
      mockitoWhen(orderRepository.getInvoiceNumber(readyOrder)).thenReturn(Future(13))
      mockitoWhen(sNSSender.publish(mockMatchers.anyObject(), mockMatchers.anyObject())(mockMatchers.anyObject(), mockMatchers.anyObject()))
        .thenReturn(Future.successful(\/ right new PublishResult().withMessageId("MyId")))
      mockitoWhen(dateUtils.now).thenReturn(nowDateAfterCutOffTIme)

      And("Spreedly payment gateway is invoked")
      mockitoWhen(paymentGateway.pay(mockMatchers.any[SpreedlyRequest])).thenReturn(Future(successfulPaymentResp))

      Then("it should throw InvalidRequestException")
      intercept[InvalidRequestException] {
        Await.result(paymentService.processPayment(orderId, userId, paymentRequest, paymentMethodService), futureWaitDuration)
      }
    }
  }

  def purchaseCompleteEquals(response: OrderMessage, expectedResponse: OrderMessage) = {
    val expectedPaymentMethod = expectedResponse.order.payment.get.paymentMethod.copy(id = response.order.payment.get.paymentMethod.id)
    val expectedPayment = expectedResponse.order.payment.get.copy(paymentMethod = expectedPaymentMethod)
    response should equal(expectedResponse.copy(order = expectedResponse.order.copy(orderReference = response.order.orderReference,payment = Some(expectedPayment))))
  }

}

trait PaymentServiceImplTestFixture extends MockitoSugar with Matchers with JsonMessageFormats {

  implicit val ec = ExecutionContext.Implicits.global

  val futureWaitDuration = 2 seconds

  val cutoffTime = "15:00"

  val orderId = UUID.randomUUID().toString

  implicit val system = ActorSystem("test-actor")
  val testDate = DateSerializerUtil.dateStringToZonedDateTime("2016-09-09T08:00:00+01:00")

  val orderRepository = mock[OrderRepositoryImpl]

  val paymentGateway = mock[PaymentGatewayImpl]

  val sequenceService = mock[SequenceService]
  mockitoWhen(sequenceService.nextValueFor(org.mockito.Matchers.anyString())) thenReturn Future.successful(13l)

  val dummyPaymentMethodRep = new DummyPaymentMethodRepoImpl
  val paymentMethodService = new PaymentMethodServiceImpl(dummyPaymentMethodRep, paymentGateway)
  val snsClient = mock[AmazonSNSScalaClient]
  val notificationSettings = NotificationsSettings("my-topic", "http://sns.endpoint")
  val sNSSender = mock[SNSPublisher]
  val purchaseCompletePublisherActor = system.actorOf(SNSPublisherActor.props(sNSSender, notificationSettings))
  val tableTopCutOffTime = DateSerializerUtil.dateStringToZonedDateTime("2016-09-08T15:00:00+01:00")
  val nowDateBeforeCutOffTIme = DateSerializerUtil.dateStringToZonedDateTime("2016-09-08T10:00:00+01:00")
  val nowDateAfterCutOffTIme = DateSerializerUtil.dateStringToZonedDateTime("2016-09-08T15:10:00+01:00")
  val catalogueClient = new BasketServiceApiTest.DummyCatalogueClient

  val basketConfig = BasketConfig(Some(MoneyDom.asJodaMoney(5)), 0.5, cutoffTime, MoneyDom.asJodaMoney(8))
  val dateUtils = mock[DateUtils]

  mockitoWhen(dateUtils.now).thenReturn(nowDateBeforeCutOffTIme)

  implicit val tradingDays = new TradingDays(dateUtils, basketConfig)
  val paymentService = new PaymentServiceImpl(orderRepository, paymentGateway, purchaseCompletePublisherActor, basketConfig, sequenceService)

  val basketItemDom = BasketItem(
    "abgadgdiajkbdak",
    "Product " + Random.nextInt,
    Random.nextString(6),
    2,
    MoneyDom.asJodaMoney(10),
    None,
    MoneyDom.asJodaMoney(5),
    leadTime = 1,
    0.2
  )
  val basketDom = Basket(
    2,
    MoneyDom.asJodaMoney(30),
    MoneyDom.asJodaMoney(10),
    None,
    MoneyDom.asJodaMoney(5),
    MoneyDom.asJodaMoney(40),
    List(basketItemDom)
  )


  val address = Address("5 cheapSide, officeserve", Some("St paul's"), None, "EC2V 6AA", "London", Some("work"), Some("make a quick delivery"))

  val invoiceAddress = InvoiceAddress(None, "Lionel Messi", address)

  val deliveryAddress = DeliveryAddress(None, "momomm", address)

  val addressRep = AddressRep(address.addressLine1, address.addressLine2, address.addressLine3, address.postCode, address.city, address.label, address.additionalInfo)

  val invoiceAddressRep = InvoiceAddressRep(None, "Lionel Messi", addressRep)

  val userId = "1234"

  val user = UserRep("Joe", "joe@Hotmail.com", "joe@Hotmail.com")

  val paymentMethodToken = "daolnegodailngdoawowprfalw09t4iqeragnfja"

  val paymentRequest = PaymentRequest(PaymentDetail(PaymentMethodType.creditCardType, Some(paymentMethodToken), invoiceAddressRep), user, NapkinsPlatesRep)

  val spreedlyRequest = SpreedlyRequest(orderId, 400, "GBP", paymentMethodToken, true)
  val cardPayment= CardPayment(token = paymentMethodToken, cardType = "visa", truncatedCardRep = "XXXX-XXXX-XXXX-1111", toBeRetained = false)

  val ccPaymentMethod = PaymentMethod(id = "01",
    userId = userId,
    token = Some(paymentMethodToken),
    user.username,
    paymentType = PaymentMethodType.creditCardType,
    isDefault = false,
    cardPayment = Some(cardPayment),
    label = PaymentMethod.label(PaymentMethodType.creditCardType , Some(cardPayment)),
    invoiceAddress = invoiceAddress)

  val readyOrder = Order(
    orderId,
    "1234",
    basketDom,
    testDate,
    Started,
    None,
    None,
    Some(testDate),
    Some(tradingDays.getSlots.head),
    Some(deliveryAddress),
    None,
    Some("0788988990"),
    None,
    NoTableware(33)
  )

  val alreadyProcessedOrder = Order(
    orderId,
    "1234",
    basketDom,
    testDate,
    Processed,
    Some(13),
    None,
    Some(testDate),
    Some(tradingDays.getSlots.head),
    Some(deliveryAddress),
    Some(invoiceAddress),
    Some("0788988990"),
    Some(
      PaymentResponse(
        Succeeded,
        Some(paymentMethodToken),
        Some("54"),
        ccPaymentMethod
      )
    ),
    NapkinsPlates(33)
  )

  val successfulPaymentResp = SpreedlyResponse(Succeeded, true, "visa", "XXXX-XXXX-XXXX-1111", paymentMethodToken, Some("54"))
  val failedPaymentResp = SpreedlyResponse(Failed("invalid Proces"), true, "visa", "XXXX-XXXX-XXXX-1111", paymentMethodToken, Some("54"))


  val updatedOrderSuccessfulPayment = Order(
    orderId,
    "1234",
    basketDom,
    testDate,
    Pending,
    Some(13),
    None,
    Some(testDate),
    Some(tradingDays.getSlots.head),
    Some(deliveryAddress),
    Some(invoiceAddress),
    Some("0788988990"),
    Some(
      PaymentResponse(
        Succeeded,
        Some(paymentMethodToken),
        Some("54"),
        ccPaymentMethod
      )
    ),
    NapkinsPlates(33)
  )

  // properties driven test data
  val missingDeliveryAddress = "An order with missing delivery address"
  val missingDeliveryAddress_data = OrderRequiredFields(None, Some(invoiceAddress), Some(ZonedDateTime.now()), None, Some("0788988990"), None, None, None, Some(ccPaymentMethod))

  val missingDeliveryDate = "An order with missing delivery Date"
  val missingDeliveryDate_data = OrderRequiredFields(Some(deliveryAddress), Some(invoiceAddress), None, None, Some("0788988990"), None, None, None, Some(ccPaymentMethod))

  val missingTelephoneNum = "An order with missing telephone number"
  val missingTelephoneNum_data = OrderRequiredFields(Some(deliveryAddress), Some(invoiceAddress), Some(ZonedDateTime.now()), None, None, None, None, None, Some(ccPaymentMethod))

  val missingDeliverySlot = "An order with missing delivery slot"
  val missingDeliverySlot_data = OrderRequiredFields(Some(deliveryAddress), Some(invoiceAddress), Some(ZonedDateTime.now()), None, None, None, None, None, Some(ccPaymentMethod))

  val requiredFieldsBeforePayment = Table(
    ("testCase", "testData"),
    (missingDeliveryAddress, missingDeliveryAddress_data),
    (missingDeliveryDate, missingDeliveryDate_data),
    (missingTelephoneNum, missingTelephoneNum_data)
  )

  def expectedPurchaseComplete(order: Order = updatedOrderSuccessfulPayment) = OrderMessage(order, UserDom(userId, user.name, user.username, user.email))
}

case class OrderRequiredFields(deliveryAddress: Option[DeliveryAddress],
                               invoiceAddress: Option[InvoiceAddress],
                               deliveryDate: Option[ZonedDateTime],
                               deliverySlot: Option[DeliverySlot],
                               telephoneNum: Option[String],

                               invoiceNumber: Option[Long],
                               transactionId: Option[String],
                               gatewayTransactionId: Option[String],
                               paymentMethod: Option[PaymentMethod]) {

  val payment = for {
    transactionId <- this.transactionId
    gatewayTransactionId <- this.gatewayTransactionId
    paymentMethod <- this.paymentMethod
  } yield {
    PaymentResponse(
      paymentStatus = Succeeded,
      transactionId = this.transactionId,
      gatewayTransactionId = this.gatewayTransactionId,
      paymentMethod = paymentMethod
    )
  }

}
