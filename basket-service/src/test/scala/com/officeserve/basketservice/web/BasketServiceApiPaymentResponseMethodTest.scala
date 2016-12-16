//package com.officeserve.basketservice.web
//
//import java.util.UUID
//
//import com.officeserve.basketservice.clients.{JsonMessageFormats, PaymentGatewayImpl}
//import com.officeserve.basketservice.persistence.{Address, CardPayment, InvoiceAddress, PaymentMethod, PaymentMethodRepository, PaymentMethodRepositoryImpl, SpreedlyResponse, Succeeded}
//import com.officeserve.basketservice.service._
//import com.officeserve.basketservice.settings.{BasketConfig, BasketSettings}
//import com.officeserve.basketservice.web.BasketServiceApiPaymentResponseMethodTest.DummyPaymentMethodRepoImpl
//import com.officeserve.basketservice.web.BasketServiceApiTest.DummyCatalogueClient
//import com.typesafe.scalalogging.StrictLogging
//import officeserve.commons.domain.MoneyDom
//import officeserve.commons.spray.auth.{TrustedAuth, `X-Cognito-Identity-Id`}
//import officeserve.commons.spray.webutils.Error
//import org.mockito.Mockito.{when => mockitoWhen, _}
//import org.mockito.{Matchers => mockMatchers}
//import org.scalatest.concurrent.ScalaFutures
//import org.scalatest.mock.MockitoSugar
//import org.scalatest.time.{Millis, Seconds, Span}
//import org.scalatest.{BeforeAndAfter, FeatureSpec, GivenWhenThen, Matchers}
//import spray.http.ContentTypes._
//import spray.http.HttpEntity
//import spray.http.StatusCodes._
//import spray.httpx.SprayJsonSupport
//import spray.testkit.ScalatestRouteTest
//
//import scala.concurrent.{ExecutionContext, Future}
//
//
///**
//  * Created by mo on 17/10/2016.
//  */
//class BasketServiceApiPaymentResponseMethodTest extends
//  FeatureSpec
//  with Matchers
//  with SprayJsonSupport
//  with ScalatestRouteTest
//  with BasketJsonProtocol
//  with MockitoSugar
//  with GivenWhenThen
//  with BasketServiceApiPaymentMethodTestFixure
//  with BeforeAndAfter
//  with StrictLogging
//  with ScalaFutures
//  with JsonMessageFormats {
//  val settings = new BasketSettings()
//  implicit val dbSettings = settings.dynamoDBSettings
//  implicit val ec = ExecutionContext.Implicits.global
//  val paymentService = mock[PaymentServiceImpl]
//  val basketService = mock[BasketServiceImpl]
//  val orderService = mock[OrderServiceImpl]
//  val deliveryService = mock[DeliveryServiceImpl]
//  val catalogueClient = mock[DummyCatalogueClient]
//  val DummyPaymentMethodRep = new DummyPaymentMethodRepoImpl
//  val paymentMethodRepository = new PaymentMethodRepositoryImpl
//  val paymentGatway = mock[PaymentGatewayImpl]
//  val basketConfig = BasketConfig(Some(MoneyDom.asJodaMoney(5)), 0.5, "15:00", MoneyDom.asJodaMoney(8))
//  implicit val tradingDays = new TradingDays(DateUtils(), basketConfig)
//  val paymentMethodServiceWithDummyRepo = new PaymentMethodServiceImpl(DummyPaymentMethodRep, paymentGatway)
//  val paymentMethodServices = new PaymentMethodServiceImpl(paymentMethodRepository, paymentGatway)
//
//  val basketItemApiWithDummyRepo = new BasketServiceApi(basketService, paymentService, orderService, paymentMethodServiceWithDummyRepo, catalogueClient,deliveryService, basketConfig)
//  val basketItemApi = new BasketServiceApi(basketService, paymentService, orderService, paymentMethodServices, catalogueClient, deliveryService, basketConfig)
//
//  implicit val defaultPatience =
//    PatienceConfig(timeout = Span(10, Seconds), interval = Span(500, Millis))
//
//
//  before {
//    reset(paymentGatway)
//  }
//
//  feature("paymentMethod Service") {
//
//    scenario("retain and persist a new paymentMethod") {
//      Given("I have payment method to persist")
//      When("I call paymentMethodSerive to persist")
//      mockitoWhen(paymentGatway.verifyAndRetain(mockMatchers.any[String], mockMatchers.any[Boolean]))
//        .thenReturn(Future.successful(Right(spreedlyResponse)))
//
//      Then("it should verify and retain with spreedly then persist the new paymentMethod")
//      Post("/paymentMethod").withEntity(HttpEntity(`application/json`, jsonPaymentMethodRequest(paymentMethodToken, false, username))).withHeaders(`X-Cognito-Identity-Id`(Some(userId))) ~> basketItemApi.routes ~> check {
//        status shouldBe Created
//        responseAs[PaymentMethodResponseRep].success.isDefined shouldBe true
//        responseAs[PaymentMethodResponseRep].success.size shouldBe 1
//        val respon = responseAs[PaymentMethodResponseRep].success.head
//        respon.map { p =>
//          p.paymentType shouldBe PaymentMethodType.creditCardType
//          p.isDefault shouldBe false
//          p.cardPayment.get.token shouldBe paymentMethodToken
//          p.cardPayment.get.cardType shouldBe cardType
//          p.cardPayment.get.truncatedCardRep shouldBe truncatedCard
//        }
//      }
//      verify(paymentGatway).verifyAndRetain(mockMatchers.any[String], mockMatchers.any[Boolean])
//
//    }
//
//    scenario("return all paymentMethods for a given user") {
//      Given("i have userId and i want to fetch all paymentMethods")
//      When("i call paymentMethodSerice to get all saved paymentMethods")
//      Then("it should return all saved paymentMethods")
//      Get(s"/paymentMethods/byUsername/${username}").withHeaders(`X-Cognito-Identity-Id`(Some(userId))) ~> basketItemApiWithDummyRepo.routes ~> check {
//        status shouldBe OK
//        val result = responseAs[PaymentMethodResponseRep].success.get
//        result.toList.sortBy(_.id) shouldBe expectedPaymentMethods.toList.sortBy(_.id)
//      }
//    }
//
//    scenario("saving a new default payment method") {
//      val newUserId = UUID.randomUUID().toString
//      val newPaymentMethodToken = UUID.randomUUID().toString
//      val newUsername = newUserId + "@officeserve.com"
//
//      Given("I have payment method to persist as default payment")
//      When("I call paymentMethodSerive to persist")
//      mockitoWhen(paymentGatway.verifyAndRetain(mockMatchers.any[String], mockMatchers.any[Boolean]))
//        .thenReturn(Future.successful(Right(spreedlyResponse)))
//
//      Then("it should verify and retain with spreedly then persist the new paymentMethod")
//      Post("/paymentMethod").withEntity(HttpEntity(`application/json`, jsonPaymentMethodRequest(newPaymentMethodToken, true, newUsername))).withHeaders(`X-Cognito-Identity-Id`(Some(newUserId))) ~> basketItemApi.routes ~> check {
//        status shouldBe Created
//        logger.debug(response.entity.asString)
//      }
//
//      And("this payment Method shoulbe saved as default")
//      whenReady(paymentMethodRepository.getPaymentMethodsByUserId(newUserId)) {
//        case Right(paymentMethods) => {
//          val defaults = paymentMethods.filter(_.isDefault)
//          defaults.size shouldBe 1
//          logger.debug("fetched default test PaymentMethod:" + defaults.head)
//          defaults.head.token shouldBe Some(newPaymentMethodToken)
//          logger.debug("the expected token: " + newPaymentMethodToken)
//        }
//        case Left(e) => logger.error("could not fetch the database for test", e)
//      }
//    }
//
//    scenario("updating on account payment method to be default") {
//      val newUserId = UUID.randomUUID().toString
//      val newUsername = newUserId + "@officeserve.com"
//
//      Given("An already existing ON_ACCOUNT payment method")
//
//      val onAccount = PaymentMethod(
//        userId = newUserId,
//        paymentType = "ON_ACCOUNT",
//        username = newUsername,
//        isDefault = false,
//        label = "On Account",
//        invoiceAddress = invoiceAddress
//      )
//      val pms = Set(onAccount)
//      whenReady(paymentMethodRepository.batchCreatePaymentMethods(pms)) { _ =>
//
//        When("I update it with isDefault = true")
//        Put(s"/paymentMethod/${onAccount.id}").withEntity(HttpEntity(`application/json`,
//          jsonUpdatePaymentMethodRequest(None, true, newUsername, invoiceAddress))
//        ).withHeaders(`X-Cognito-Identity-Id`(Some(newUserId))) ~> basketItemApi.routes ~> check {
//
//          Then("Response should be OK")
//          println(response.entity.toString)
//          status shouldBe OK
//          And("It should be marked as default")
//          whenReady(paymentMethodRepository.getPaymentMethodById(onAccount.id)) { c =>
//            c.right.get.isDefault shouldBe true
//          }
//
//        }
//
//      }
//
//
//    }
//
//    scenario("updating invoice address in an on account payment method") {
//      val newUserId = UUID.randomUUID().toString
//      val newUsername = newUserId + "@officeserve.com"
//
//      Given("An already existing ON_ACCOUNT payment method")
//
//      val onAccount = PaymentMethod(
//        userId = newUserId,
//        paymentType = "ON_ACCOUNT",
//        username = newUsername,
//        isDefault = false,
//        label = "On Account",
//        invoiceAddress = invoiceAddress
//      )
//      val pms = Set(onAccount)
//      whenReady(paymentMethodRepository.batchCreatePaymentMethods(pms)) { _ =>
//
//        When("I update the invoice address")
//        Put(s"/paymentMethod/${onAccount.id}").withEntity(HttpEntity(`application/json`,
//          jsonUpdatePaymentMethodRequest(None, true, newUsername, invoiceAddress.copy(fullName = "Updated Name")))
//        ).withHeaders(`X-Cognito-Identity-Id`(Some(newUserId))) ~> basketItemApi.routes ~> check {
//
//          Then("Response should be 'Forbidden'")
//          status shouldBe Forbidden
//
//        }
//
//      }
//
//
//    }
//
//    scenario("updating invoice address in a credit card payment method") {
//      val newUserId = UUID.randomUUID().toString
//      val newUsername = newUserId + "@officeserve.com"
//      val token = newUserId + UUID.randomUUID().toString
//
//      Given("An already existing CREDIT_CARD payment method")
//
//      val creditCard = PaymentMethod(
//        userId = newUserId,
//        paymentType = "CREDIT_CARD",
//        username = newUsername,
//        isDefault = false,
//        label = "On Account",
//        invoiceAddress = invoiceAddress,
//        token = Some(token),
//        cardPayment = Some(CardPayment(token,"VISA", "XXXX-111", false))
//      )
//      val updatedInvoiceAddress = invoiceAddress.copy(fullName = "Updated Name")
//
//      val pms = Set(creditCard)
//      whenReady(paymentMethodRepository.batchCreatePaymentMethods(pms)) { _ =>
//
//        When("I update the invoice address")
//        Put(s"/paymentMethod/${creditCard.id}").withEntity(HttpEntity(`application/json`,
//          jsonUpdatePaymentMethodRequest(Some(token), true, newUsername, updatedInvoiceAddress))
//        ).withHeaders(`X-Cognito-Identity-Id`(Some(newUserId))) ~> basketItemApi.routes ~> check {
//
//          Then("Response should be 'OK'")
//          status shouldBe OK
//          And("Invoice Address should be updated")
//          whenReady(paymentMethodRepository.getPaymentMethodById(creditCard.id)) { c =>
//            c.right.get.invoiceAddress shouldBe updatedInvoiceAddress
//          }
//        }
//
//      }
//
//    }
//
//    scenario("capturing userId for paymentMethods with an unKnownUser ") {
//      val newUserId = UUID.randomUUID().toString
//      val paymentMethodId = UUID.randomUUID().toString
//
//      Given("given i have a one saved paymentMethod with my username but with an Unknown userId")
//      val newUsername = newUserId + "@officeserve.com"
//      val paymentMethodUnknownUser = PaymentMethod(id = paymentMethodId,
//        userId = UserRep.unknownUserId,
//        token = Some("01-token"),
//        newUsername,
//        paymentType = PaymentMethodType.creditCardType,
//        isDefault = false,
//        cardPayment = Some(CardPayment(token = "01-token", cardType = cardType, truncatedCardRep = truncatedCard, toBeRetained = true)),
//        label = "some-label",
//        invoiceAddress = invoiceAddress)
//      val expectedPaymentMethodUpdatedUser = paymentMethodUnknownUser.copy(userId = newUserId)
//
//      whenReady(paymentMethodRepository.savePaymentMethod(paymentMethodUnknownUser)) { _ =>
//        When("I fetch all my paymentMethods first time ")
//        Get(s"/paymentMethods/byUsername/${newUsername}").withHeaders(`X-Cognito-Identity-Id`(Some(newUserId))) ~> basketItemApi.routes ~> check {
//          status shouldBe OK
//          val resp = responseAs[PaymentMethodResponseRep].success
//          resp.isDefined shouldBe true
//          resp.get.size shouldBe 1
//          resp.get.exists(_.id == paymentMethodUnknownUser.id) shouldBe true
//        }
//      }
//
//      whenReady(paymentMethodRepository.getPaymentMethodById(paymentMethodId)) {
//        case Right(paymentMethod) => paymentMethod shouldBe expectedPaymentMethodUpdatedUser
//        case Left(e) =>
//          logger.error("could not fetch the database for test", e)
//          fail(s"$e")
//      }
//
//    }
//
//  }
//}
//
//trait BasketServiceApiPaymentMethodTestFixure {
//
//  val userId = "4544"
//
//  val cardType = "visa"
//
//  val truncatedCard = "XXXX-XXXX-XXXX-1111"
//
//  val username = "m.ahmed@hotmail.com"
//
//  val paymentMethodToken = UUID.randomUUID().toString
//
//  val address = Address(addressLine1 = "5 cheapSide, officeserveasda", addressLine2 = Some("cheapside, St pauls"), None, postCode = "EC2V 6AA", city = "London")
//
//  val invoiceAddress = InvoiceAddress(None, "Diego Armando Maradona", address)
//
//  val addressRep = AddressRep(addressLine1 = "5 cheapSide, officeserveasda", addressLine2 = Some("cheapside, St pauls"), None, postCode = "EC2V 6AA", city = "London")
//
//  val invoiceAddressRep = InvoiceAddressRep(None, "Diego Armando Maradona", addressRep)
//
//  val cardPaymentRep = CardPaymentRep(token = "", cardType = cardType, truncatedCardRep = truncatedCard)
//
//  val spreedlyResponse = SpreedlyResponse(paymentStatus = Succeeded,
//    retainedToken = true,
//    cardType = cardType,
//    truncatedCardRep = truncatedCard,
//    gatewayTransactionId = Some("54"),
//    transactionId = "jasgajsdfjsd")
//
//  val expectedPaymentMethods = Set(PaymentMethodRep(id = "01",
//    paymentType = PaymentMethodType.creditCardType,
//    isDefault = false,
//    cardPayment = Some(cardPaymentRep.copy(token = "01-token")),
//    label = "some-label",
//    invoiceAddress = invoiceAddressRep),
//
//    PaymentMethodRep(id = "02",
//      paymentType = PaymentMethodType.creditCardType,
//      isDefault = false,
//      cardPayment = Some(cardPaymentRep.copy(token = "02-token")),
//      label = "some-label",
//      invoiceAddress = invoiceAddressRep),
//
//    PaymentMethodRep(id = "03",
//      paymentType = PaymentMethodType.creditCardType,
//      isDefault = true,
//      cardPayment = Some(cardPaymentRep.copy(token = "03-token")),
//      label = "some-label",
//      invoiceAddress = invoiceAddressRep))
//
//  def jsonPaymentMethodRequest(paymentMethodToken: String, isDefault: Boolean, username: String) =
//    s"""
//       |	{
//       |         "token" : "${paymentMethodToken}",
//       |         "isDefault": ${isDefault},
//       |         "username": "${username}",
//       |        "invoiceAddress" : {
//       |          "fullName": "${invoiceAddress.fullName}",
//       |          "address": {
//       |          "addressLine1" : "${invoiceAddress.address.addressLine1}",
//       |          "addressLine2" : "${invoiceAddress.address.addressLine2}" ,
//       |          "postCode" : "${invoiceAddress.address.postCode}" ,
//       |          "city" : "${invoiceAddress.address.city}"
//       |          }
//       |        }
//       |      }
//    """.stripMargin
//
//  def jsonUpdatePaymentMethodRequest(paymentMethodToken: Option[String], isDefault: Boolean, username: String, invoiceAddress: InvoiceAddress = invoiceAddress) = {
//    "{\n" +
//      paymentMethodToken.map { token =>
//        s"""
//           | "token" : "${token}",
//     """.stripMargin
//      }.getOrElse("") +
//      s"""
//         |        "isDefault": ${isDefault},
//         |         "username": "${username}",
//         |        "invoiceAddress" : {
//         |          "fullName": "${invoiceAddress.fullName}",
//         |          "address": {
//         |          "addressLine1" : "${invoiceAddress.address.addressLine1}",
//         |          "addressLine2" : "${invoiceAddress.address.addressLine2.getOrElse("")}" ,
//         |          "postCode" : "${invoiceAddress.address.postCode}" ,
//         |          "city" : "${invoiceAddress.address.city}"
//         |          }
//         |        }
//         |      }
//    """.stripMargin
//  }
//
//
//}
//
//object BasketServiceApiPaymentResponseMethodTest {
//
//
//  class DummyPaymentMethodRepoImpl extends PaymentMethodRepository with BasketServiceApiPaymentMethodTestFixure {
//
//    override def getPaymentMethodById(id: String): Future[Either[Error, PaymentMethod]] = ???
//
//    override def getPaymentMethodsByUsername(userId: String, userName: String): Future[Either[Error, Set[PaymentMethod]]] = Future.successful {
//      Right(Set(
//        PaymentMethod(id = "01",
//          userId = userId,
//          token = Some("01-token"),
//          "m.a@officeserve",
//          paymentType = PaymentMethodType.creditCardType,
//          isDefault = false,
//          cardPayment = Some(CardPayment(token = "01-token", cardType = cardType, truncatedCardRep = truncatedCard, toBeRetained = true)),
//          label = "some-label",
//          invoiceAddress = invoiceAddress),
//
//        PaymentMethod(id = "02",
//          userId = userId,
//          token = Some("02-token"),
//          "m.a@officeserve",
//          paymentType = PaymentMethodType.creditCardType,
//          isDefault = false,
//          cardPayment = Some(CardPayment(token = "02-token", cardType = cardType, truncatedCardRep = truncatedCard, toBeRetained = true)),
//          label = "some-label",
//          invoiceAddress = invoiceAddress),
//
//        PaymentMethod(id = "03",
//          userId = userId,
//          token = Some("03-token"),
//          "m.a@officeserve",
//          paymentType = PaymentMethodType.creditCardType,
//          isDefault = true,
//          cardPayment = Some(CardPayment(token = "03-token", cardType = cardType, truncatedCardRep = truncatedCard, toBeRetained = true)),
//          label = "some-label",
//          invoiceAddress = invoiceAddress))
//      )
//    }
//
//    override def savePaymentMethod(paymentMethod: PaymentMethod): Future[Either[Error, PaymentMethod]] = ???
//
//    override def getPaymentMethodsByUserId(userId: String): Future[Either[Error, Set[PaymentMethod]]] = Future.successful {
//      Right(Set(
//        PaymentMethod(id = "01",
//          userId = userId,
//          token = Some("01-token"),
//          "m.a@officeserve",
//          paymentType = PaymentMethodType.creditCardType,
//          isDefault = false,
//          cardPayment = Some(CardPayment(token = "01-token", cardType = cardType, truncatedCardRep = truncatedCard, toBeRetained = true)),
//          label = "some-label",
//          invoiceAddress = invoiceAddress),
//
//        PaymentMethod(id = "02",
//          userId = userId,
//          token = Some("02-token"),
//          "m.a@officeserve",
//          paymentType = PaymentMethodType.creditCardType,
//          isDefault = false,
//          cardPayment = Some(CardPayment(token = "02-token", cardType = cardType, truncatedCardRep = truncatedCard, toBeRetained = true)),
//          label = "some-label",
//          invoiceAddress = invoiceAddress),
//
//        PaymentMethod(id = "03",
//          userId = userId,
//          token = Some("03-token"),
//          "m.a@officeserve",
//          paymentType = PaymentMethodType.creditCardType,
//          isDefault = true,
//          cardPayment = Some(CardPayment(token = "03-token", cardType = cardType, truncatedCardRep = truncatedCard, toBeRetained = true)),
//          label = "some-label",
//          invoiceAddress = invoiceAddress))
//      )
//    }
//
//    override def getPaymentMethodByToken(token: String): Future[Either[Error, PaymentMethod]] = ???
//
//    override def updatePaymentMethodUserID(username: String, userId: String): Future[Either[Error, Unit]] = ???
//
//    override def updatePaymentMethodInvoiceAddress(id: String, invoiceAddress: InvoiceAddress): Future[Either[Error, Unit]] = ???
//
//    override def deletePaymentMethod(id: String): Future[Either[Error, Unit]] = ???
//
//    /**
//      * only used for On_account paymentMethod creation in [[com.officeserve.basketservice.service.PaymentMethodServiceAdmin]]
//      */
//    override def getPaymentMethodsByUsername(userName: String): Future[Either[Error, Set[PaymentMethod]]] = ???
//
//    override def batchCreatePaymentMethods(paymentMethods: Set[PaymentMethod]): Future[Either[Error, Unit]] = ???
//  }
//
//  /**
//    *
//    * {"isDefault":true,
//    * "token":"Y3oxk0ssKLvnZP9ABCd0v5lQRjj",
//    * "invoiceAddress":{"fullName":"sadasd", "address":{"addressLine1":"asdasd","postCode":"asdasd","city":"sadsad"}},
//    * "username":"p.chermanowicz@officeserve.com"}
//    * *
//    * {"isDefault":true,
//    * "invoiceAddress":{"fullName":"Peter Chrermanowicz", "address":{"addressLine1":"sdfasdf","city":"dsfdsaf","postCode":"sdfsafas","addressLine3":"asdfdasf","addressLine2":"sdfasdf"}},
//    * "username":"p.chermanowicz@officeserve.com"}
//    *
//    */
//
//}