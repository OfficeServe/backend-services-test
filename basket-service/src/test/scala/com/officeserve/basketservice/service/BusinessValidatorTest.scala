package com.officeserve.basketservice.service

import java.time.ZonedDateTime
import java.util.UUID

import com.officeserve.basketservice.persistence._
import com.officeserve.basketservice.service.BusinessValidator._
import com.officeserve.basketservice.settings.BasketConfig
import com.officeserve.basketservice.web.PaymentMethodType
import officeserve.commons.domain.{MoneyDom, UserDom}
import org.scalatest.mock.MockitoSugar
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.{FeatureSpec, GivenWhenThen, Matchers}

import scala.collection.immutable.List
import scala.util.Random

class BusinessValidatorTest extends FeatureSpec with GivenWhenThen with BusinessValidatorTestFixture {
  info("BusinessValidator should validate all business rules")

  feature("Mininum order for tableTop") {
    scenario("Total above minimum order value") {
      Given("A minimum order value")
      val minimumOrderValue = MoneyDom.asJodaMoney(23)

      And("An order total which is above it")
      val total = MoneyDom.asJodaMoney(35)

      When("BusinessValidator meetsTableTopMinOrder is invoked")
      val minimumOrder = meetsTableTopMinOrder(minimumOrderValue, total)

      Then("It should return true")
      assert(minimumOrder == true, "minimumOrder should be met")
    }
    scenario("Total below minimum order value") {
      Given("A minumum order value")
      val minimumOrderValue = MoneyDom.asJodaMoney(20)

      And("An order total which is below it")
      val total = MoneyDom.asJodaMoney(10)

      When("BusinessValidator meetsTableTopMinOrder is invoked")
      val minimumOrder = meetsTableTopMinOrder(minimumOrderValue, total)

      Then("It should return false")
      assert(minimumOrder == false, "minimumOrder should not be met")
    }
  }

  feature("Check for required fields for Order ") {

    scenario("An order is ready to be processed or updated :") {
      Given("I have an incomplete order")
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
        MoneyDom.asJodaMoney(5),
        List(basketItemDom)
      )
      val order = Order(
        UUID.randomUUID().toString,
        "1234",
        basketDom,
        ZonedDateTime.now(),
        Started,
        Some(22),
        None,
        Some(ZonedDateTime.now()),
        Some(tradingDays.getSlots.head),
        None,
        None,
        Some("0774789987"),
        None,
        NoTableware(0)
      )

      When("BusinessValidator checkForRequiredFields is invoked")
      Then("it should throw an InvalidRequestException for missing ")
      intercept[InvalidRequestException] {
        checkForRequiredFields(order, true)
      }
    }
  }

  feature("validate after payment-attempted ") {
    forAll(requiredFieldsAfterPayment) { (testCase, testData) => {
      scenario(testCase) {
        Given("Payment service has a response from payment gateway and now persisting the updated order ")
        val order = Order(
          orderId,
          "1234",
          basketDom,
          ZonedDateTime.now(),
          Started,
          testData.invoiceNumber,
          None,
          testData.deliveryDate,
          None,
          testData.deliveryAddress,
          testData.invoiceAddress,
          testData.telephoneNum,
          testData.payment,
          NoTableware(0)
        )
        When("OrderRepository update order  is invoked ")
        Then("it should throw an InvalidRequestException for missing required fields")
        intercept[InvalidRequestException] {
          BusinessValidator.checkForRequiredFields(order, true)
        }
      }
    }
    }
  }
}

trait BusinessValidatorTestFixture extends MockitoSugar with Matchers {
  val orderId = UUID.fromString("067e6162-3b6f-4ae2-a171-2470b63dff00").toString

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
    MoneyDom.asJodaMoney(5),
    List(basketItemDom)
  )


  val addressDomain = Address("5 cheapSide, officeserver", Some("St paul's"), None, "EC2V 6AA", "London", Some("work"))

  val invoiceAddressDomain = InvoiceAddress(Some("Lionel Cayman Ltd"), "Lionel Messi", addressDomain)

  val paymentMethod = PaymentMethod(id = "01",
    userId = "some-userId",
    token = Some(paymentMethodToken),
    "m.a@officeserve",
    paymentType = PaymentMethodType.creditCardType,
    isDefault = false,
    cardPayment = Some(CardPayment(token = paymentMethodToken, cardType = "visa", truncatedCardRep = "XXXX-XXXX-XXXX-1111", toBeRetained = true)),
    label = "some-label",
    invoiceAddress = invoiceAddressDomain)

  val deliveryAddress = DeliveryAddress(None, "mommm,", addressDomain)

  val user = UserDom("ahgaiu", "Joe", "joe@Hotmail.com", "joe@Hotmail.com")

  val paymentMethodToken = "nadkgdhasnolegdnsal"

  val basketConfig = BasketConfig(Some(MoneyDom.asJodaMoney(5)), 0.5, "15:00", MoneyDom.asJodaMoney(8))
  implicit val tradingDays = new TradingDays(new DateUtils, basketConfig)

  // properties driven test data
  val missingDeliveryAddress = "An order with missing delivery address"
  val missingDeliveryAddress_data = OrderRequiredFields(None, Some(invoiceAddressDomain), Some(ZonedDateTime.now), None, Some("07677888888"), None, None, None, None)

  val missingDeliveryDate = "An order with missing delivery Date"
  val missingDeliveryDate_data = OrderRequiredFields(Some(deliveryAddress), Some(invoiceAddressDomain), None, None, Some("07677888888"), None, None, None, None)

  val missingDeliverySlot = "An order with missing delivery Date"
  val missingDeliverySlot_data = OrderRequiredFields(Some(deliveryAddress), Some(invoiceAddressDomain), Some(ZonedDateTime.now), None, Some("07677888888"), None, None, None, None)

  val missingTelephoneNum = "An order with missing telephone number"
  val missingTelephoneNum_data = OrderRequiredFields(Some(deliveryAddress), Some(invoiceAddressDomain), Some(ZonedDateTime.now), Some(tradingDays.getSlots.head), None, None, None, None, None)

  val missingInvoiceNumber = "An order with missing invoice Number"
  val missingInvoiceNumber_data = OrderRequiredFields(Some(deliveryAddress), Some(invoiceAddressDomain), Some(ZonedDateTime.now), Some(tradingDays.getSlots.head), Some("07677888888"), None, Some("hdiaids"), Some("hdiaids"), None)

  val missingTransactionId = "An order with missing transaction Id"
  val missingTransactionId_data = OrderRequiredFields(Some(deliveryAddress), Some(invoiceAddressDomain), Some(ZonedDateTime.now), Some(tradingDays.getSlots.head), Some("07677888888"), Some(13), None, Some("hdiaids"), None)

  val missingGatewayTransactionId = "An order with a missing Gateway Transaction Id"
  val missingGatewayTransactionId_data = OrderRequiredFields(Some(deliveryAddress), Some(invoiceAddressDomain), Some(ZonedDateTime.now), Some(tradingDays.getSlots.head), Some("07677888888"), Some(13), Some("agena"), None, None)

  val missingCardType = "An order with a missing card type"
  val missingCardType_data = OrderRequiredFields(Some(deliveryAddress), Some(invoiceAddressDomain), Some(ZonedDateTime.now), Some(tradingDays.getSlots.head), Some("07677888888"), Some(13), Some("agena"), Some("hdiaids"), None)

  val missingCardNumRepresentation = "An order with a missing card representation"
  val missingCardNumRepresentation_data = OrderRequiredFields(Some(deliveryAddress), Some(invoiceAddressDomain), Some(ZonedDateTime.now), Some(tradingDays.getSlots.head), Some("07677888888"), Some(13), Some("agena"), Some("hdiaids"), None)

  val missingPaymentMethod = "An order with a missing payment"
  val missingPaymentMethod_data = OrderRequiredFields(Some(deliveryAddress), Some(invoiceAddressDomain), Some(ZonedDateTime.now), Some(tradingDays.getSlots.head), Some("07677888888"), Some(13), Some("agena"), Some("hdiaids"), None)

  val requiredFieldsAfterPayment = Table(
    ("testCase", "testData"),
    (missingDeliveryAddress, missingDeliveryAddress_data),
    (missingDeliveryDate, missingDeliveryDate_data),
    (missingTelephoneNum, missingTelephoneNum_data),
    (missingTransactionId, missingTransactionId_data),
    (missingGatewayTransactionId, missingGatewayTransactionId_data),
    (missingPaymentMethod, missingPaymentMethod_data))

}

