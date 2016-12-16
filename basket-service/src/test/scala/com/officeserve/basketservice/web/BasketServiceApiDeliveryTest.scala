//package com.officeserve.basketservice.web
//
//import com.officeserve.basketservice.clients.{CatalogueClient, JsonMessageFormats}
//import com.officeserve.basketservice.persistence.{Covered, PostCode, PostCodeRepositoryImpl, UpComing}
//import com.officeserve.basketservice.service._
//import com.officeserve.basketservice.settings.{BasketConfig, BasketSettings}
//import com.typesafe.scalalogging.StrictLogging
//import officeserve.commons.domain.MoneyDom
//import officeserve.commons.spray.auth.`X-Cognito-Identity-Id`
//import org.scalatest.{BeforeAndAfter, FeatureSpec, GivenWhenThen, Matchers}
//import org.scalatest.concurrent.ScalaFutures
//import org.scalatest.mock.MockitoSugar
//import spray.http.StatusCodes._
//import spray.httpx.SprayJsonSupport
//import spray.testkit.ScalatestRouteTest
//import org.mockito.Mockito.{when => mockitoWhen, _}
//import org.mockito.{Matchers => mockMatchers}
//import org.scalatest.prop.TableDrivenPropertyChecks._
//import org.scalatest.prop.Tables.Table
//
//import scala.concurrent.{ExecutionContext, Future}
//
///**
//  * Created by mo on 02/12/2016.
//  */
//class BasketServiceApiDeliveryTest extends
//  FeatureSpec
//  with BasketServiceApiDeliveryTestFixture
//  with Matchers
//  with SprayJsonSupport
//  with ScalatestRouteTest
//  with BasketJsonProtocol
//  with MockitoSugar
//  with GivenWhenThen
//  with BeforeAndAfter
//  with StrictLogging
//  with ScalaFutures
//  with JsonMessageFormats {
//
//  val settings = new BasketSettings()
//  val basketConfig = BasketConfig(Some(MoneyDom.asJodaMoney(5)), 0.5, "15:00", MoneyDom.asJodaMoney(8))
//  implicit val dbSettings = settings.dynamoDBSettings
//  implicit val ec = ExecutionContext.Implicits.global
//  implicit val tradingDays = new TradingDays(DateUtils(), basketConfig)
//
//  // mocked Services
//  val basketService = mock[BasketServiceImpl]
//  val paymentService = mock[PaymentServiceImpl]
//  val orderService = mock[OrderServiceImpl]
//  val paymentMethodService = mock[PaymentMethodServiceImpl]
//  val catalogueClient = mock[CatalogueClient]
//  val postcodeRepository = mock[PostCodeRepositoryImpl]
//
//  // instantiated services
//  val deliveryService = new DeliveryServiceImpl(postcodeRepository)
//  val basketServiceApi = new BasketServiceApi(basketService, paymentService, orderService, paymentMethodService, catalogueClient, deliveryService, basketConfig)
//
//  // commmons vals
//  val userId = "2233"
//
//  feature("Delivery service") {
//    forAll(postCodeTestData) { (testCase, postcode, area, areaName, coverageStatus) => {
//      scenario(testCase) {
//        Given("i have a postCode to verify")
//
//        val cStatus = coverageStatus match {
//          case UpComing.status => UpComing
//          case Covered.status => Covered
//        }
//
//        val p = PostCode(area, areaName, cStatus, None, true)
//        mockitoWhen(postcodeRepository.getPostcodeArea(area)).thenReturn(Future.successful(Right(p)))
//
//        When(s"/delivery/postcodeCoverage/$postcode")
//        Get(s"/delivery/postcodeCoverage/$postcode").withHeaders(`X-Cognito-Identity-Id`(Some(userId))) ~> basketServiceApi.routes ~> check {
//          status shouldBe OK
//          val result = responseAs[DeliveryResponseRep].success.get
//          result shouldBe List(PostCodeRep(area, areaName, coverageStatus))
//        }
//      }
//    }
//    }
//  }
//
//}
//
//trait BasketServiceApiDeliveryTestFixture {
//  val postCodeTestData = Table(
//    ("testCase", "postcode", "area", "areaName", "coverageStatus"),
//    ("covered postcode", "EC2V6AA", "EC", "London", Covered.status),
//    ("Upcoming postcode", "BS148TH", "BS", "Bristol", UpComing.status))
//}
