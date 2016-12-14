package com.officeserve.basketservice.web

import java.time.{ZoneId, ZonedDateTime}
import java.time.format.DateTimeFormatter
import java.util.UUID

import com.google.inject.Guice
import com.officeserve.basketservice.AppModule
import com.officeserve.basketservice.clients._
import com.officeserve.basketservice.persistence._
import com.officeserve.basketservice.service._
import com.officeserve.basketservice.settings.{BasketConfig, NotificationsSettings}
import com.officeserve.basketservice.web.BasketServiceApiTest.TestPercentageOffPromo
import net.codingwell.scalaguice.InjectorExtensions._
import officeserve.commons.domain.{Discount, MoneyDom, UserDom}
import officeserve.commons.spray.auth.`X-Cognito-Identity-Id`
import officeserve.commons.spray.webutils.{DateSerializerUtil, Error, Price}
import org.mockito.Mockito.{when => mockitoWhen, _}
import org.mockito.{Matchers => mockMatchers}
import org.scalatest.mock.MockitoSugar
import org.scalatest.{BeforeAndAfter, FeatureSpec, GivenWhenThen, Matchers}
import spray.http.ContentTypes._
import spray.http.HttpEntity
import spray.http.StatusCodes._
import spray.httpx.SprayJsonSupport
import spray.testkit.ScalatestRouteTest

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Random, Try}
import DateSerializerUtil._

/**
  * Created by mo on 24/05/2016.
  */
class BasketServiceApiTest extends FeatureSpec
  with Matchers
  with SprayJsonSupport
  with ScalatestRouteTest
  with BasketServiceApiTestFixture
  with BasketServiceApiJsonRequests
  with BasketJsonProtocol
  with MockitoSugar
  with GivenWhenThen
  with BeforeAndAfter
  with JsonMessageFormats {

  import BasketServiceApiTest._

  val timeout = 10 seconds

  val injector = Guice.createInjector(new AppModule())

  val pricingService = new MockPricingServiceImpl

  val orderRepository = injector.instance[OrderRepository]

  val postCodeRepository = mock[PostCodeRepositoryImpl]

  val deliveryService = new DeliveryServiceImpl(postCodeRepository)

  val promoRepository = new DummyPromoRepository

  val promotionService = new PromotionServiceImpl(orderRepository, promoRepository)

  val spreedlyClient = mock[PaymentGatewayImpl]

  val paymentService = mock[PaymentServiceImpl]

  val catalogueClient = new DummyCatalogueClient

  val notificationSettings = NotificationsSettings("my-topic", "http://sns.endpoint")

  val snsSender = mock[SNSPublisher]

  val purchaseCompletePublisherActor = system.actorOf(SNSPublisherActor.props(snsSender, notificationSettings))

  val basketService = new BasketServiceImpl(pricingService, promotionService, basketConfigLowMinimumOrderValue)

  val orderService = new OrderServiceImpl(orderRepository, basketConfigLowMinimumOrderValue, purchaseCompletePublisherActor, catalogueClient, paymentService, deliveryService)(tradingDays)

  val basketItemApi = new BasketServiceApi(basketService, paymentService, orderService, paymentMethodService, catalogueClient, deliveryService, basketConfigLowMinimumOrderValue)

  def createBasketServiceApi(snsSender:SNSPublisher)(implicit tradingDays: TradingDays) = {
    val purchaseCompletePublisherActor = system.actorOf(SNSPublisherActor.props(snsSender, notificationSettings))
    val orderService = new OrderServiceImpl(orderRepository, basketConfigLowMinimumOrderValue, purchaseCompletePublisherActor, catalogueClient, paymentService, deliveryService)(tradingDays)
    new BasketServiceApi(basketService, paymentService, orderService, paymentMethodService, catalogueClient, deliveryService, basketConfigLowMinimumOrderValue)
  }

  before {
    reset(snsSender)
  }

  feature("Health check") {
    scenario("OK") {
      Given("that everything is working")
      When("/healthcheck is invoked")
      Then("It should return status 200")
      Get(s"/healthcheck") ~> basketItemApi.routes ~> check {
        status shouldBe OK
      }
    }
  }

  feature("/basket") {
    scenario("Should calculate and return a basket") {
      Given("A valid order")
      When("/basket is invoked")
      mockitoWhen(dateUtilsMock.now).thenReturn(nowDateBeforeCutOffTIme)
      Then("it should return a calculated basket with all the required fields")
      Post("/basket").withEntity(HttpEntity(`application/json`, jsonBasketRequest)).withHeaders(`X-Cognito-Identity-Id`(Some(userId))) ~> basketItemApi.routes ~> check {
        status shouldBe OK
        responseAs[BasketRep].itemsCount shouldBe 3
        Try[Price](responseAs[BasketRep].totalPrice).isSuccess shouldBe true
        Try[Price](responseAs[BasketRep].totalVAT).isSuccess shouldBe true
        Try[Price](responseAs[BasketRep].grandTotal).isSuccess shouldBe true
        Try[Price](responseAs[BasketRep].deliveryCharge).isSuccess shouldBe true
        Try[Option[PromoRep]](responseAs[BasketRep].promotion).isSuccess shouldBe true
        responseAs[BasketRep].items.forall(_.quantity >= 1) shouldBe true
        responseAs[BasketRep].items.forall(_.price.value isValidInt) shouldBe true
        responseAs[BasketRep].items.forall(_.price.currency.equals("GBP")) shouldBe true
        responseAs[BasketRep].items.forall(_.productId.isEmpty()) shouldBe false
      }
    }

    scenario("Minimum order met") {
      Given("a very low minimum order value")
      val basketServiceWithLowMinimumOrderValue = new BasketServiceImpl(pricingService, promotionService, basketConfigLowMinimumOrderValue)
      val orderService2 = new OrderServiceImpl(orderRepository, basketConfigLowMinimumOrderValue, purchaseCompletePublisherActor, catalogueClient, paymentService, deliveryService)(tradingDays)
      val basketItemApi2 = new BasketServiceApi(basketServiceWithLowMinimumOrderValue, paymentService, orderService2, paymentMethodService, catalogueClient, deliveryService, basketConfigLowMinimumOrderValue)

      And("An order which is above that value")

      When("/basket is invoked")
      mockitoWhen(dateUtilsMock.now).thenReturn(nowDateBeforeCutOffTIme)
      Then("It should not return any minimum order hints")
      Post("/basket").withEntity(HttpEntity(`application/json`, jsonBasketRequest)).withHeaders(`X-Cognito-Identity-Id`(Some(userId))) ~> basketItemApi2.routes ~> check {
        status shouldBe OK
        responseAs[BasketRep].hints.map(_.find(_.name == ServiceHint.basketMinimumOrder_key)).isEmpty shouldBe true
      }
    }

    scenario("Minimum order not met") {
      Given("a very high minimum order value")
      val basketServiceWithHighMinimumOrderValue = new BasketServiceImpl(pricingService, promotionService, basketConfigHighMinimumOrderValue)
      val orderService3 = new OrderServiceImpl(orderRepository, basketConfigHighMinimumOrderValue, purchaseCompletePublisherActor, catalogueClient, paymentService, deliveryService)(tradingDays)
      val basketItemApi3 = new BasketServiceApi(basketServiceWithHighMinimumOrderValue, paymentService, orderService3, paymentMethodService, catalogueClient, deliveryService, basketConfigHighMinimumOrderValue)

      And("An order which is below that value")

      When("/basket is invoked")
      mockitoWhen(dateUtilsMock.now).thenReturn(nowDateBeforeCutOffTIme)
      Then("It should return a single minimum order hint")
      Post("/basket").withEntity(HttpEntity(`application/json`, jsonBasketRequest)).withHeaders(`X-Cognito-Identity-Id`(Some(userId))) ~> basketItemApi3.routes ~> check {
        status shouldBe OK
        responseAs[BasketRep].hints.map(_.find(_.name == ServiceHint.basketMinimumOrder_key)).isDefined shouldBe true
      }
    }
  }

  feature("/orders - startOrder") {
    scenario("Minimum order met") {
      Given("a very low minimum order value")
      implicit val tradingDays = new TradingDays(dateUtilsMock, basketConfigLowFromDateLong)
      val basketServiceWithLowMinimumOrderValue = new BasketServiceImpl(pricingService, promotionService, basketConfigLowFromDateLong)
      val orderService4 = new OrderServiceImpl(orderRepository, basketConfigLowFromDateLong, purchaseCompletePublisherActor, catalogueClient, paymentService, deliveryService)
      val basketItemApi4 = new BasketServiceApi(basketServiceWithLowMinimumOrderValue, paymentService, orderService4, paymentMethodService, catalogueClient, deliveryService, basketConfigLowFromDateLong)

      And("An order which is above that value")

      And("An authenticated user")
      When("/orders is invoked")
      mockitoWhen(dateUtilsMock.now).thenReturn(nowDateTimeFromDateTest)

      Then("it should return a StartedOrder with all the required fields")
      Post("/orders").withEntity(HttpEntity(`application/json`, jsonBasketRequest)).withHeaders(`X-Cognito-Identity-Id`(Some(userId))) ~> basketItemApi4.routes ~> check {
        status shouldBe Created
        // If the id field isn't a valid UUID, the JSON parsing will fail anyway
        val startOrder = responseAs[StartOrderRep]
        startOrder.order.basket.itemsCount shouldBe 3
        startOrder.order.basket.items.forall(_.quantity >= 1) shouldBe true
        startOrder.order.basket.items.forall(_.price.value isValidInt) shouldBe true
        startOrder.order.basket.items.forall(_.price.currency.equals("GBP")) shouldBe true
        startOrder.order.basket.items.forall(_.productId.isEmpty()) shouldBe false
        startOrder.order.tableware shouldBe 45
        startOrder.blockDate shouldBe expectBlockDate
      }
    }

    scenario("Minimum order not met") {
      Given("a very high minimum order value")
      val basketServiceWithHighMinimumOrderValue = new BasketServiceImpl(pricingService, promotionService, basketConfigHighMinimumOrderValue)
      val orderService3 = new OrderServiceImpl(orderRepository, basketConfigHighMinimumOrderValue, purchaseCompletePublisherActor, catalogueClient, paymentService, deliveryService)(tradingDays)
      val basketItemApi3 = new BasketServiceApi(basketServiceWithHighMinimumOrderValue, paymentService, orderService3, paymentMethodService, catalogueClient, deliveryService, basketConfigHighMinimumOrderValue)

      And("An order which is below that value")
      val entity = HttpEntity(`application/json`, jsonBasketRequest)

      And("An authenticated user")

      When("/orders is invoked")
      Then("It should return a HTTP status code of 400 Bad Request with an appropriate error object")
      Post("/orders").withEntity(entity).withHeaders(`X-Cognito-Identity-Id`(Some(userId))) ~> basketItemApi3.routes ~> check {
        status shouldBe BadRequest
        responseAs[Error].code shouldBe 1000
      }
    }
  }

  feature("/payment") {
    scenario("Successful payment transaction") {
      Given("A successful payment transaction")
      mockitoWhen(paymentService.processPayment(org.mockito.Matchers.any[String],
        org.mockito.Matchers.any[String],
        org.mockito.Matchers.any[PaymentRequest],
        org.mockito.Matchers.any[PaymentMethodService]))
        .thenReturn(Future.successful(purchasCompleteRep))

      When("payment endpoint is invoked")
      Then("It should return payment method response")
      Post(s"/payment/order/$orderId").withEntity(HttpEntity(`application/json`, jsonPaymentPayRequest_credit_card)).withHeaders(`X-Cognito-Identity-Id`(Some(userId))) ~> basketItemApi.routes ~> check {
        status shouldBe OK
      }
    }

    scenario("Failed payment transaction") {
      Given("A failed payment transaction")
      mockitoWhen(paymentService.processPayment(org.mockito.Matchers.any[String], org.mockito.Matchers.any[String], org.mockito.Matchers.any[PaymentRequest], org.mockito.Matchers.any[PaymentMethodService]))
        .thenReturn(Future.failed(PaymentFailedException("failed")))

      When("payment endpoint is invoked")
      Then("It should return payment method response")
      Post(s"/payment/order/$orderId").withEntity(HttpEntity(`application/json`, jsonPaymentPayRequest_credit_card)).withHeaders(`X-Cognito-Identity-Id`(Some(userId))) ~> basketItemApi.routes ~> check {
        status shouldBe UnprocessableEntity
        responseAs[officeserve.commons.spray.webutils.Error] shouldBe expectedError
      }
    }
  }

  feature("/orders - update") {
    scenario("now is before cut-off") {
      Given("i have already created and order and i want add/update delivery date")
      val basketItemApiBeforeCutOff = new BasketServiceApi(basketService, paymentService, orderService, paymentMethodService, catalogueClient, deliveryService, basketConfigLowMinimumOrderValue)
      Await.result(orderRepository.createOrder(readyToUpdateOrder), 2 seconds)

      And("I have a validate update request json")
      val entity = HttpEntity(`application/json`, jsonUpdateOrderReqBeforeCutOff)

      And("An authenticated user")
      And("now time is before cutOffTime")
      mockitoWhen(dateUtilsMock.now).thenReturn(nowDateBeforeCutOffTIme)
      mockitoWhen(postCodeRepository.getPostcodeArea(mockMatchers.anyString())).thenReturn(Future.successful(Right(PostCode("EC", "London", Covered, None, true))))

      When("Patch /basket/orders/{orderId}/update is invoked")

      Then("it should return Success.ok status and update fields should be persisted")
      Patch(s"/orders/$orderId").withEntity(entity).withHeaders(`X-Cognito-Identity-Id`(Some(userId))) ~> basketItemApiBeforeCutOff.routes ~> check {
        status shouldBe OK
        val response = Await.result(orderRepository.getOrder(orderId), 2 seconds)
        response.isDefined shouldBe true
        response.foreach(o => ordersEquals(o, expectedUpdatedOrderBeforeCutOffTime))
      }

    }

    scenario("now is after cut-off") {
      Given("i have already created and order and i want add/update delivery date")
      val basketItemApiBeforeCutOff = new BasketServiceApi(basketService, paymentService, orderService, paymentMethodService, catalogueClient, deliveryService, basketConfigLowMinimumOrderValue)
      Await.result(orderRepository.createOrder(readyToUpdateOrder), 2 seconds)

      And("I have a validate update request json")
      val entity = HttpEntity(`application/json`, jsonUpdateOrderReqAfterCutOff)

      And("An authenticated user")
      And("now time is after cutOffTime")
      mockitoWhen(dateUtilsMock.now).thenReturn(nowDateAfterCutOffTIme)

      When("Patch /basket/orders/{orderId}/update is invoked")


      Then("it should return Success.ok status and update fields should be persisted")
      Patch(s"/orders/$orderId").withEntity(entity).withHeaders(`X-Cognito-Identity-Id`(Some(userId))) ~> basketItemApiBeforeCutOff.routes ~> check {
        status shouldBe OK
        val response = Await.result(orderRepository.getOrder(orderId), 2 seconds)
        response.isDefined shouldBe true
        response.foreach(o => ordersEquals(o, expectedUpdatedOrderAfterCutOffTime))
      }
    }

    scenario("deliveryDate is on a weekend") {
      Given("i have already created and order and i want add/update delivery date")
      val basketItemApiBeforeCutOff = new BasketServiceApi(basketService, paymentService, orderService, paymentMethodService, catalogueClient, deliveryService, basketConfigLowMinimumOrderValue)
      Await.result(orderRepository.createOrder(readyToUpdateOrder), 2 seconds)

      And("I have a validate update request json")
      val entity = HttpEntity(`application/json`, jsonUpdateOrderReqWeekend)

      And("An authenticated user")
      And("the selected deliveryDate is on a weekend")
      mockitoWhen(dateUtilsMock.now).thenReturn(nowDateAfterCutOffTIme)
      mockitoWhen(postCodeRepository.getPostcodeArea(mockMatchers.anyString())).thenReturn(Future.successful(Right(PostCode("EC", "London", Covered, None, true))))

      When("Patch /basket/orders/{orderId}/update is invoked")


      Then("it should return BadRequest.400 status and reject the update")
      Patch(s"/orders/$orderId").withEntity(entity).withHeaders(`X-Cognito-Identity-Id`(Some(userId))) ~> basketItemApiBeforeCutOff.routes ~> check {
        status shouldBe BadRequest
        responseAs[officeserve.commons.spray.webutils.Error] shouldBe expectedErrorDeliveryDateWeekend
      }
    }

    scenario("deliveryDate is on a holiday or non-trading day") {
      Given("i have already created and order and i want add/update delivery date")
      val basketItemApiBeforeCutOff = new BasketServiceApi(basketService, paymentService, orderService,paymentMethodService, catalogueClient, deliveryService, basketConfigLowMinimumOrderValue)
      Await.result(orderRepository.createOrder(readyToUpdateOrder), 2 seconds)

      And("I have a validate update request json")
      val entity = HttpEntity(`application/json`, jsonUpdateOrderReqHoliday)

      And("An authenticated user")
      And("the selected deliveryDate is on a Holiday or non trading day")
      mockitoWhen(dateUtilsMock.now).thenReturn(nowDateAfterCutOffTIme)

      When("Patch /basket/orders/{orderId}/update is invoked")


      Then("it should return BadRequest.400 status and reject the update")
      Patch(s"/orders/$orderId").withEntity(entity).withHeaders(`X-Cognito-Identity-Id`(Some(userId))) ~> basketItemApiBeforeCutOff.routes ~> check {
        status shouldBe BadRequest
        responseAs[officeserve.commons.spray.webutils.Error] shouldBe expectedErrorDeliveryDateHoliday
      }
    }

    scenario("deliveryDate is before the available from date") {


      val basketItemApiBeforeCutOff = new BasketServiceApi(basketService, paymentService, orderService,paymentMethodService, catalogueClient, deliveryService, basketConfigLowMinimumOrderValue)
      Await.result(orderRepository.createOrder(readyToUpdateOrder), 2 seconds)

      And("I have a validate update request json")
      val entity = HttpEntity(`application/json`, jsonUpdateOrderReqBeforeFromDate)

      And("An authenticated user")
      And("the selected deliveryDate is before the available delivery fromDate")

      mockitoWhen(dateUtilsMock.now).thenReturn(nowDateAfterCutOffTIme)

      When("Patch /basket/orders/{orderId}/update is invoked")


      Then("it should return BadRequest.400 status and reject the update")
      Patch(s"/orders/$orderId").withEntity(entity).withHeaders(`X-Cognito-Identity-Id`(Some(userId))) ~> basketItemApiBeforeCutOff.routes ~> check {
        status shouldBe BadRequest
        responseAs[officeserve.commons.spray.webutils.Error] shouldBe expectedErrorDeliveryDateWrongFromDate
      }
    }

    scenario("invalid telephone number") {
      Given("i have already created and order and i want add/update telephone number")
      val basketItemApiBeforeCutOff = new BasketServiceApi(basketService, paymentService, orderService, paymentMethodService, catalogueClient, deliveryService, basketConfigLowMinimumOrderValue)
      Await.result(orderRepository.createOrder(readyToUpdateOrder), 2 seconds)

      And("I have a validate update request json")
      val entity = HttpEntity(`application/json`, jsonUpdateOrderReqTelphoneNum)

      And("An authenticated user")
      And("the telephone format is invalid")
      mockitoWhen(dateUtilsMock.now).thenReturn(nowDateAfterCutOffTIme)

      When("Patch /basket/orders/{orderId}/update is invoked")


      Then("it should return BadRequest.400 status and reject the update")
      Patch(s"/orders/$orderId").withEntity(entity).withHeaders(`X-Cognito-Identity-Id`(Some(userId))) ~> basketItemApiBeforeCutOff.routes ~> check {
        status shouldBe BadRequest
        responseAs[officeserve.commons.spray.webutils.Error] shouldBe expectedErrorTelephoneFormat
      }
    }

    scenario("not covered postCode") {
      Given("i have already created and order and i want add/update delivery address")
      val basketItemApiBeforeCutOff = new BasketServiceApi(basketService, paymentService, orderService, paymentMethodService, catalogueClient, deliveryService, basketConfigLowMinimumOrderValue)
      Await.result(orderRepository.createOrder(readyToUpdateOrder), 2 seconds)

      And("I have a validate update request json")
      val entity = HttpEntity(`application/json`, jsonUpdateOrderReqDeliveryAddress)

      And("An authenticated user")
      And("the telephone format is invalid")
      mockitoWhen(dateUtilsMock.now).thenReturn(nowDateAfterCutOffTIme)
      mockitoWhen(postCodeRepository.getPostcodeArea(mockMatchers.anyString())).thenReturn(Future.successful(Right(PostCode("UB", "Bristol", UpComing, None, true))))

      When("Patch /basket/orders/{orderId}/update is invoked")


      Then("it should return BadRequest.400 status and reject the update")
      Patch(s"/orders/$orderId").withEntity(entity).withHeaders(`X-Cognito-Identity-Id`(Some(userId))) ~> basketItemApiBeforeCutOff.routes ~> check {
        status shouldBe BadRequest
        responseAs[officeserve.commons.spray.webutils.Error] shouldBe expectedErrorNotCoveredAddress
      }
    }
  }

  feature("orders - get ") {
    scenario("Get orders for a user") {
      Given("A user with existing orders")
      //create the orders
      val myOrder = order.copy(userId = user.id)
      val myOrders = Await.result(
        Future.sequence {
          List(orderRepository.createOrder(myOrder),
            orderRepository.createOrder(myOrder.copy(id = UUID.randomUUID().toString, orderStatus = Processed)),
            orderRepository.createOrder(myOrder.copy(id = UUID.randomUUID().toString, orderStatus = Cancelled)))
        } flatMap { _ => orderRepository.getUserOrders(user.id) },
        timeout)
      val inProgress = myOrders.filter(_.orderStatus == Started)
      val succeeded = myOrders.filter(_.orderStatus == Succeeded)
      val cancelled = myOrders.filter(_.orderStatus == Cancelled)
      val orders2Return = myOrders.size - inProgress.size
      And(s"${inProgress.size} order(s) is/are 'In Progress', ${succeeded.size} is/are (Payment)'Succeeded' and ${cancelled.size} is/are Cancelled")
      When("orders endpoint is invoked")
      Then(s"It should only return $orders2Return orders")
      Get(s"/orders").withHeaders(`X-Cognito-Identity-Id`(Some(user.id))) ~> basketItemApi.routes ~> check {
        status shouldBe OK
        responseAs[PageResponse[OrderRep]].summary.total should be > 0
        responseAs[PageResponse[OrderRep]].summary.total shouldBe orders2Return
      }
    }
  }

  feature("orders/{id} - cancel") {
    scenario("Cancel an existing pending order") {
      //create the orders
      val myOrder = order.copy(
        userId = user.id,
        id = UUID.randomUUID().toString,
        orderStatus = Pending,
        deliveryDate = Some(tradingDays.getDeliveryFromDay(order.maxLeadTime)))
      Await.result(orderRepository.createOrder(myOrder), timeout)
      Given(s"A user with an existing order in status == ${myOrder.orderStatus}")
      When("cancel order orders endpoint is invoked")
      Then(s"It should only return 204")
      Put(s"/orders/${myOrder.id}/cancel").withEntity(HttpEntity(`application/json`, jsonCancelOrderRequest)).withHeaders(`X-Cognito-Identity-Id`(Some(user.id))) ~> basketItemApi.routes ~> check {
        println(responseAs[String])
        status shouldBe OK
      }
      And("email notification should be triggered")
      org.mockito.Mockito.verify(snsSender).publish(mockMatchers.anyObject(), mockMatchers.anyObject())(mockMatchers.anyObject(), mockMatchers.anyObject())
    }

    scenario("Cancel an already cancelled order") {
      //create the orders
      val myOrder = order.copy(userId = user.id,
        id = UUID.randomUUID().toString,
        orderStatus = Cancelled,
        deliveryDate = Some(tradingDays.getDeliveryFromDay(order.maxLeadTime)))
      Await.result(orderRepository.createOrder(myOrder), timeout)
      Given(s"A user with an existing cancelled order")
      When("cancel order orders endpoint is invoked")
      Then(s"It should only return 204")
      Put(s"/orders/${myOrder.id}/cancel").withEntity(HttpEntity(`application/json`, jsonCancelOrderRequest)).withHeaders(`X-Cognito-Identity-Id`(Some(user.id))) ~> basketItemApi.routes ~> check {
        println(responseAs[String])
        status shouldBe OK
      }
      And("email notification should not be triggered")
      org.mockito.Mockito.verifyZeroInteractions(snsSender)
    }

    scenario("Cancel an already cancelled order after cutoff") {
      //create the orders
      val myOrder = order.copy(userId = user.id,
        id = UUID.randomUUID().toString,
        orderStatus = Cancelled,
        deliveryDate = Some(tradingDays.getDeliveryFromDay(order.maxLeadTime).minusDays(2)))
      Await.result(orderRepository.createOrder(myOrder), timeout)
      Given(s"A user with an existing cancelled order")
      When("cancel order orders endpoint is invoked")
      Then(s"It should only return 204")
      Put(s"/orders/${myOrder.id}/cancel").withEntity(HttpEntity(`application/json`, jsonCancelOrderRequest)).withHeaders(`X-Cognito-Identity-Id`(Some(user.id))) ~> basketItemApi.routes ~> check {
        status shouldBe OK
      }
      And("email notification should not be triggered")
      org.mockito.Mockito.verifyZeroInteractions(snsSender)
    }

    /*
    TODO: uncomment once we implement Delayed Payment
    scenario("Reject cancellation of an 'already processed' order") {
      //create the orders
      val myOrder = order.copy(userId = user.id, id = UUID.randomUUID().toString, orderStatus = Processed)
      Await.result(orderRepository.createOrder(myOrder), timeout)
      Given(s"A user with an existing checkout in progress")
      When("cancel order orders endpoint is invoked")
      Then(s"It should only return 403")
      Put(s"/orders/${myOrder.id}/cancel").withEntity(HttpEntity(`application/json`, jsonCancelOrderRequest)).withHeaders(`X-Cognito-Identity-Id`(Some(user.id))) ~> basketItemApi.routes ~> check {
        status shouldBe Forbidden
      }
      And("email notification should not be triggered")
      org.mockito.Mockito.verifyZeroInteractions(sNSSender)
    }
    */

    scenario("Reject cancellation of an 'in progress' order") {
      //create the orders
      val myOrder = order.copy(userId = user.id, id = UUID.randomUUID().toString, orderStatus = Started,
        deliveryDate = Some(tradingDays.getDeliveryFromDay(order.maxLeadTime)))
      Await.result(orderRepository.createOrder(myOrder), timeout)
      Given(s"A user with an existing checkout in progress")
      When("cancel order orders endpoint is invoked")
      Then(s"It should only return 403")
      Put(s"/orders/${myOrder.id}/cancel").withEntity(HttpEntity(`application/json`, jsonCancelOrderRequest)).withHeaders(`X-Cognito-Identity-Id`(Some(user.id))) ~> basketItemApi.routes ~> check {
        status shouldBe Forbidden
      }
      And("email notification should not be triggered")
      org.mockito.Mockito.verifyZeroInteractions(snsSender)
    }

    scenario("Reject cancellation of an 'unexisting' order") {
      Given(s"A an invalid order id")
      val orderId = "wrongOrderID"
      When("cancel order orders endpoint is invoked")
      Then(s"It should return 404")
      Put(s"/orders/$orderId/cancel").withEntity(HttpEntity(`application/json`, jsonCancelOrderRequest)).withHeaders(`X-Cognito-Identity-Id`(Some(user.id))) ~> basketItemApi.routes ~> check {
        status shouldBe NotFound
      }
      And("email notification should not be triggered")
      org.mockito.Mockito.verifyZeroInteractions(snsSender)
    }

    scenario("Reject cancellation of an existing pending order after cutoff") {
      //create the orders
      val myOrder = order.copy(userId = user.id,
        id = UUID.randomUUID().toString, orderStatus = Pending,
        deliveryDate = Some(tradingDays.getDeliveryFromDay(order.maxLeadTime).minusDays(2)))
      Await.result(orderRepository.createOrder(myOrder), timeout)
      Given(s"A user with an existing order in status == ${myOrder.orderStatus}")
      When("cancel order orders endpoint is invoked")
      Then(s"It should only return 204")
      Put(s"/orders/${myOrder.id}/cancel").withEntity(HttpEntity(`application/json`, jsonCancelOrderRequest)).withHeaders(`X-Cognito-Identity-Id`(Some(user.id))) ~> basketItemApi.routes ~> check {
        status shouldBe Forbidden
      }
      And("email notification should be triggered")
      org.mockito.Mockito.verifyZeroInteractions(snsSender)
    }

  }

  def ordersEquals(response: Order, expectedResponse: Order) = {
    response shouldBe expectedResponse.copy(orderReference = response.orderReference)
  }

}

trait BasketServiceApiTestFixture extends Adapter with MockitoSugar {
  val dateUtilsMock = mock[DateUtils]

  val paymentMethodService = mock[PaymentMethodServiceImpl]

  val cutoffTime = "15:00"

  val paymentMethodToken = "daolnegodailngdoawowprfalw09t4iqeragnfja"

  val nowDateBeforeCutOffTIme = DateSerializerUtil.dateStringToZonedDateTime("2016-09-08T10:00:00+01:00")

  val nowDateAfterCutOffTIme = DateSerializerUtil.dateStringToZonedDateTime("2016-09-08T15:10:00+01:00")

  val nowDateAfterCutOffTIme_expectedFromDate = DateSerializerUtil.dateStringToZonedDateTime("2016-09-12T00:00:00+01:00")

  val nowDateTimeFromDateTest = DateSerializerUtil.dateStringToZonedDateTime("2016-12-23T10:10:00+01:00")

  val tableTopCutOffTimeFromDateTest = DateSerializerUtil.dateStringToZonedDateTime("2016-12-23T15:00:00+00:00")

  val tableTopCutOffTime = DateSerializerUtil.dateStringToZonedDateTime("2016-09-08T15:00:00+01:00")

  val dateFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

  val expectedFromDateBeforeCutOffTime = DateSerializerUtil.dateStringToZonedDateTime("2017-01-03T00:00:00+00:00")

  val basketConfigLowMinimumOrderValue = BasketConfig(Some(MoneyDom.asJodaMoney(5)), 0.5, cutoffTime, MoneyDom.asJodaMoney(8))

  val basketConfigLowFromDateLong = BasketConfig(Some(MoneyDom.asJodaMoney(5)), 0.5, cutoffTime, MoneyDom.asJodaMoney(8))

  val basketConfigHighMinimumOrderValue = BasketConfig(Some(MoneyDom.asJodaMoney(500)), 0.5, cutoffTime, MoneyDom.asJodaMoney(8))

  mockitoWhen(dateUtilsMock.now).thenReturn(nowDateTimeFromDateTest)

  implicit val tradingDays = new TradingDays(dateUtilsMock, basketConfigLowMinimumOrderValue)

  val holidays = tradingDays.holidays.map(_.withZoneSameInstant(ZoneId.of("UTC")))

  val expectBlockDate = BlockDateRep(expectedFromDateBeforeCutOffTime, holidays, tradingDays.getSlots, tableTopCutOffTimeFromDateTest)

  val userId = "2334"

  val testDate = DateSerializerUtil.dateStringToZonedDateTime("2016-06-16T12:11:06+01:00")

  val orderId = UUID.randomUUID().toString

  val address = Address("5 cheapSide, officeserve", Some("cheapside, St pauls"), None, "EC2V 6AA", "London", Some("work"), Some("make a quick delivery"))

  val invoiceAddress = InvoiceAddress(Some("Momo Industries"), "momo", address)

  val deliveryAddress = DeliveryAddress(None, "momo", address)

  val paymentMethod = PaymentMethod(id = "01",
    userId = "1234",
    token = Some(paymentMethodToken),
    "m.a@officeserve",
    paymentType = PaymentMethodType.creditCardType,
    isDefault = false,
    cardPayment = Some(CardPayment(token = paymentMethodToken, cardType = "visa", truncatedCardRep = "XXXX-XXXX-XXXX-1111", toBeRetained = true)),
    label = "some-label",
    invoiceAddress = invoiceAddress)

  val basketItemList = List(
    BasketItem("68ae0d3f-34dd-41a0-b37e-1e78a2e49670", "Product " + Random.nextInt, "1e78a2e49670", 2, MoneyDom.asJodaMoney(37), None, MoneyDom.asJodaMoney(37),  1, 0.2),
    BasketItem("66dc188f-8304-4e0f-8161-428067af60ae", "Product " + Random.nextInt, "428067af60ae", 2, MoneyDom.asJodaMoney(37.9), None, MoneyDom.asJodaMoney(37.9), 1, 0.2),
    BasketItem("4fc0c0b5-1a2d-4b4d-8a2f-589c6b081afd", "Product " + Random.nextInt, "589c6b081afd", 2, MoneyDom.asJodaMoney(2.5), None, MoneyDom.asJodaMoney(2.5), 1, 0.0))

  val promoSum = Some(PromoSummary(MoneyDom.asJodaMoney(18.85), TestPercentageOffPromo.promoCode, TestPercentageOffPromo.description, TestPercentageOffPromo.image))

  val basketDom = Basket(
    3,
    MoneyDom.asJodaMoney(75.4),
    MoneyDom.asJodaMoney(12.91),
    promoSum,
    MoneyDom.asJodaMoney(8),
    MoneyDom.asJodaMoney(77.46),
    basketItemList)

  val readyToUpdateOrder = Order(
    orderId,
    userId,
    basketDom,
    testDate,
    Started,
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    NoTableware(33),
    "12")


  val expectedUpdatedOrderBeforeCutOffTime = Order(
    orderId,
    userId,
    basketDom,
    testDate,
    Started,
    None,
    None,
    Some(DateSerializerUtil.dateStringToZonedDateTime("2016-09-09T08:00:00+01:00")),
    Some(DeliverySlot("07:00", "09:00")),
    Some(deliveryAddress),
    None,
    Some("07664324677"),
    None,
    NoTableware(33),
    Order.getOrderReference,
    None,
    None,
    Some(DateSerializerUtil.dateStringToZonedDateTime("2016-09-08T15:00:00+01:00")))

  val expectedUpdatedOrderAfterCutOffTime = Order(
    orderId,
    userId,
    basketDom,
    testDate,
    Started,
    None,
    None,
    Some(DateSerializerUtil.dateStringToZonedDateTime("2016-09-12T10:00:00+00:00")),
    Some(DeliverySlot("10:00", "12:00")),
    Some(deliveryAddress),
    None,
    Some("07664324677"),
    None,
    NoTableware(33),
    Order.getOrderReference,
    None,
    None,
    Some(DateSerializerUtil.dateStringToZonedDateTime("2016-09-09T15:00:00+01:00")))

  val order = Order(
    orderId,
    "1234",
    basketDom,
    ZonedDateTime.now(),
    Processed,
    Some(12),
    None,
    Some(ZonedDateTime.now()),
    Some(tradingDays.getSlots.head),
    Some(deliveryAddress),
    None,
    Some("0788988990"),
    Some(
      PaymentResponse(
        Succeeded,
        Some("someTransactionId"),
        Some("someTransactionId"),
        paymentMethod)
    ),
    NoTableware(0)
  )
  val user = UserDom("ahgaiu", "Joe", "joe@Hotmail.com", "joe@Hotmail.com")

  val purchasCompleteRep = OrderMessage(order, user)

  val expectedError = officeserve.commons.spray.webutils.Error(422, Some("failed"), None)

  val expectedErrorDeliveryDateWeekend = officeserve.commons.spray.webutils.Error(400, Some("invalid delivery date: we don't deliver on the Weekend"), None)

  val expectedErrorDeliveryDateHoliday = officeserve.commons.spray.webutils.Error(400, Some("invalid delivery date: non trading date selected"), None)

  val expectedErrorDeliveryDateWrongFromDate = officeserve.commons.spray.webutils.Error(400, Some("invalid delivery date: delivery should not be before the available from Date : " + DateSerializerUtil.zonedDateTimeToString(nowDateAfterCutOffTIme_expectedFromDate)), None)

  val expectedErrorTelephoneFormat = officeserve.commons.spray.webutils.Error(400, Some("invalid telephone number format"), None)

  val expectedErrorNotCoveredAddress = officeserve.commons.spray.webutils.Error(400, Some(s"postcode_status_${UpComing.status}"), None)

}


trait BasketServiceApiJsonRequests {

  val jsonCancelOrderRequest =
    """
      |{
      | "name":"Nicolas",
      | "username":"nico12345",
      | "email":"testing@officeserve.com"
      |}
    """.stripMargin

  val jsonBasketRequest =
    """ {
        "promoCode": "WELCOME25",
        "items": [
          {
            "productId": "68ae0d3f-34dd-41a0-b37e-1e78a2e49670",
            "quantity": 2
          },
          {
            "productId": "598e3993-c2c1-4f80-a4ea-7cf67a1b8e0b",
            "quantity": 2
          },
          {
            "productId": "faisolhfiaosdifnab8e0b",
            "quantity": 2
          }
        ]
      }"""

  val jsonPaymentPayRequest_credit_card =
    """{
         "paymentDetail" :{
         "paymentType" : "CREDIT_CARD",
          "paymentMethodToken": "AJGANGIEDAagndaoigdasniograeiwJFE932RASEGE",
          "invoiceAddress" : {
                "fullName" : "Lio Messi",
                "address": {
                  "addressLine1" : "5 cheapSide, officeserve",
                  "addressLine2" : "cheapside, St pauls" ,
                  "postCode" : "EC2V 6AA" ,
                  "city" : "London"
                }
              },
          "retainToken": true
        },
        "user" :{
          "name": "jopo",
          "username": "jopo12",
          "email": "jopo@gmail.com"
        },
        "tableware" : "NO_TABLEWARE"
      }
    """
  val jsonUpdateOrderReqBeforeCutOff =
    """{
        "operation": "REPLACE",
        "fields": {
            "deliveryDate" : "2016-09-09T08:00:00+01:00",
            "telephoneNum" : "07664324677",
                              "deliveryAddress" : {
                        "deliverTo" : "momo",
                        "address" : {
                            "addressLine1" : "5 cheapSide, officeserve",
                            "addressLine2" : "cheapside, St pauls" ,
                            "postCode" : "EC2V 6AA" ,
                            "city" : "London",
                            "label" : "work",
                            "additionalInfo" : "make a quick delivery"
                      }
                   }
        	}
      }
    """

  val jsonUpdateOrderReqAfterCutOff =
    """{
        "operation": "REPLACE",
        "fields": {
            "deliveryDate" : "2016-09-12T10:00:00+00:00",
            "telephoneNum" : "07664324677",
            "deliveryAddress" : {
                  "deliverTo" : "momo",
                  "address" : {
                      "addressLine1" : "5 cheapSide, officeserve",
                      "addressLine2" : "cheapside, St pauls" ,
                      "postCode" : "EC2V 6AA" ,
                      "city" : "London",
                      "label" : "work",
                      "additionalInfo" : "make a quick delivery"
                }
             }
        	}
      }
    """

  val jsonUpdateOrderReqWeekend =
    """{
        "operation": "REPLACE",
        "fields": {
            "deliveryDate" : "2016-09-11T10:00:00+01:00"
        	}
      }
    """
  val jsonUpdateOrderReqHoliday =
    """{
        "operation": "REPLACE",
        "fields": {
            "deliveryDate" : "2016-12-26T08:00:00+01:00"
        	}
      }
    """
  val jsonUpdateOrderReqBeforeFromDate =
    """{
        "operation": "REPLACE",
        "fields": {
            "deliveryDate" : "2016-09-09T08:00:00+01:00"
        	}
      }
    """
  val jsonUpdateOrderReqTelphoneNum =
    """{
        "operation": "REPLACE",
        "fields": {
          "telephoneNum" : "076643246779"
        	}
      }
    """

  val jsonUpdateOrderReqDeliveryAddress =
    """{
        "operation": "REPLACE",
        "fields": {
          "deliveryAddress": {
            "deliverTo": "messy",
            "address": {
              "addressLine1": "5 cheapSide, officeserve",
              "addressLine2": "cheapside, St pauls",
              "postCode": "UB15ijj",
              "city": "London",
              "label": "work",
              "additionalInfo": "make a quick delivery"
            }
         }
        	}
      }
    """
}

object BasketServiceApiTest {

  class DummyCatalogueClient extends CatalogueClient {
    override def getAvailableProducts(productIds: Seq[String])
                                     (implicit ec: ExecutionContext): Future[Seq[ProductRep]] = {

      Future.successful(productIds.map { id =>
        val price = 5.00
        val vat = 0.2
        ProductRep(id,
          "Product " + Random.nextInt,
          ProductPriceRep(value = price, valueIncludingVAT = price * (1 + vat), vatRate = vat),
          None,
          1,
          UnitOfMeasurementRep("people", 5),
          productCode = UUID.randomUUID().toString.take(8).toUpperCase,
          availability = true
        )
      })

    }

    override def getProducts(productIds: Seq[String])(implicit ec: ExecutionContext): Future[Seq[ProductRep]] = ???
  }

  class DummyPromoRepository extends PromoRepository {
    override val promotions: Set[Promotion] = Set(TestPercentageOffPromo)
  }

  case object TestPercentageOffPromo extends Promotion {
    override val discountAmount = 0.25
    override val promoCode = "WELCOME25"
    override val description = "25% first order discount off total"
    override val promoType = PercentageOffPromoType
    override val image: Link = Link("image", "some linnk", "image/jpg")

    override def getDiscount(totalPrice: MoneyDom, totalDelivery: MoneyDom): Discount = calculateDiscount(Some(totalPrice), None)
  }

}