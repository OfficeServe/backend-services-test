package com.officeserve.reportservice.services

import java.net.URL
import java.time.ZonedDateTime

import com.fasterxml.jackson.core.JsonParseException
import com.officeserve.basketservice.web._
import com.officeserve.documentservice.models._
import com.officeserve.reportservice.models._
import com.officeserve.sendemail.model.{EmailMessage, Html}
import officeserve.commons.spray.webutils.Price
import org.scalatest.{Matchers, path}

import scala.util.{Failure, Success}

class MessageTranslatorServiceTest extends path.FunSpec with Matchers with MessageTranslatorServiceFixtures {

  describe("MessageTranslatorService") {
    val messageTranslatorService = new MessageTranslatorServiceImpl()
    describe("when receiving a OrderMessageRep") {
      it("should convert it into an Order") {
        messageTranslatorService.fromOrderMessageRepToOrder(orderMessageRep) shouldBe Success(order)
      }
    }
    describe("when given a SQS message as json input") {
      describe("and the message had eventType = CutOffTimeProcessOrders") {
        it("should convert it into an Event") {
          messageTranslatorService.fromMessageBodyToEvent(cutOffProcessOrdersJson) shouldBe Success(Event(CutOffTimeProcessOrders, Set(orderMessageRep)))
        }
      }
      describe("and the message had eventType = PurchaseComplete") {
        it("should convert it into an Event") {
          messageTranslatorService.fromMessageBodyToEvent(purchaseCompleteJson) shouldBe Success(Event(PurchaseComplete, Set(orderMessageRep)))
        }
      }
      describe("and the message had eventType = StoreOrders") {
        it("should convert it into an Event") {
          messageTranslatorService.fromMessageBodyToEvent(storeOrderssJson) shouldBe Success(Event(StoreOrders, Set(orderMessageRep)))
        }
      }
      describe("and the message had eventType = SendOrderDocuments") {
        it("should convert it into an Event") {
          messageTranslatorService.fromMessageBodyToEvent(sendOrderDocumentsJson) shouldBe Success(Event(SendOrderDocuments, Set(orderMessageRep)))
        }
      }
    }
    describe("when given a SQS message as object input") {
      describe("and the message had eventType = SendEmail") {
        it("should serialise the message into json") {
          val json = messageTranslatorService.fromEventToMessageBody(emailMessage)
          import org.json4s.jackson.JsonMethods._
          parse(json) shouldBe parse(emailJson)
        }
      }

    }
    describe("when given a SNS message as input") {
      it("should unwrap the internal Message into an Event") {
        messageTranslatorService.fromMessageBodyToEvent(snsMessage) shouldBe Success(Event(PurchaseComplete, Set(orderMessageRep)))
      }
    }
    describe("when given a wrong input") {
      it("should return a failure") {
        messageTranslatorService.fromMessageBodyToEvent("not a json") shouldBe a[Failure[JsonParseException]]
      }
    }
  }

}

trait MessageTranslatorServiceFixtures {


  val order = Order(
    invoiceTo = InvoiceTo(
      name = "Nico Cavallo",
      address = Address(
        addressLine1 = "5 cheapSide, officeserveasda",
        addressLine2 = Some("cheapside, St pauls"),
        addressLine3 = None,
        postCode = "EC2V 6AA",
        city = "London",
        additionalInfo = None
      ),
      phone = "07444392205",
      email = "n.cavallo@officeserve.com"
    ),
    invoiceDetails = InvoiceDetails(
      date = "26/10/2016",
      invoiceNumber = "233",
      companyName = Some("OfficeServe"),
      contactName = "Nico Cavallo",
      paymentReference = Some("123456"),
      accountNumber = None,
      paymentMethod = "CREDIT_CARD",
      shippingMethod = None
    ),
    deliveryAddress = DeliveryAddress(
      companyName = Some("OfficeServe"),
      name = "Nico",
      address = Address(
        addressLine1 = "32A Denison Road",
        addressLine2 = None,
        addressLine3 = None,
        postCode = "SW19 2DH",
        city = "London",
        additionalInfo = None
      )
    ),
    deliveryDate = Some("Thu 27 Oct 2016"),
    deliverySlot = "09:00 to 11:00",
    basket = Basket(
      totalPrice = "£185.00",
      deliveryCharge = "£8.00",
      totalVat = "£38.60",
      orderTotal = "£183.00",
      invoiceTotal = "£231.60",
      promo = Some(Promo(
        description = "£10 off, test discount",
        deduction = "-£10.00"
      )),
      items = List(
        Item(
          name = "Classic Comfort Breakfast",
          productCode = "TT500201",
          quantity = "10",
          unitPrice = "£18.50",
          totalPrice = "£185.00",
          totalVat = "£37.00",
          total = "£212.00"
        )
      )
    ))

  val snsMessage =
    """{
      |	"Type": "Notification",
      |	"MessageId": "b7bbe9e8-5f6c-59ae-9370-b97d5a5dde4e",
      |	"TopicArn": "arn:aws:sns:eu-west-1:064345613152:dev_orders",
      |	"Message": "{\r\n\t\"eventType\": \"PurchaseComplete\",\r\n\t\"entities\": [{\r\n\t\t\"order\": {\r\n\t\t\t\"id\": \"56a6b54c-977f-4c79-91be-abf1431c5fb5\",\r\n\t\t\t\"userId\": \"us-east-1:f18de1da-8e98-4e94-b309-b7ffec6a5\",\r\n\t\t\t\"basket\": {\r\n\t\t\t\t\"itemsCount\": 1,\r\n\t\t\t\t\"totalPrice\": {\r\n\t\t\t\t\t\"value\": 185.0,\r\n\t\t\t\t\t\"currency\": \"GBP\"\r\n\t\t\t\t},\r\n\t\t\t\t\"totalVAT\": {\r\n\t\t\t\t\t\"value\": 38.6,\r\n\t\t\t\t\t\"currency\": \"GBP\"\r\n\t\t\t\t},\r\n\t\t\t\t\"deliveryCharge\": {\r\n\t\t\t\t\t\"value\": 8.0,\r\n\t\t\t\t\t\"currency\": \"GBP\"\r\n\t\t\t\t},\r\n\t\t\t\t\"grandTotal\": {\r\n\t\t\t\t\t\"value\": 231.6,\r\n\t\t\t\t\t\"currency\": \"GBP\"\r\n\t\t\t\t},\r\n\t\t\t\t\"items\": [{\r\n\t\t\t\t\t\"productId\": \"70adde14-bf53-410f-a08c-0def14353c8e\",\r\n\t\t\t\t\t\"name\": \"Classic Comfort Breakfast\",\r\n\t\t\t\t\t\"productCode\": \"TT500201\",\r\n\t\t\t\t\t\"quantity\": 10,\r\n\t\t\t\t\t\"price\": {\r\n\t\t\t\t\t\t\"value\": 185.0,\r\n\t\t\t\t\t\t\"currency\": \"GBP\"\r\n\t\t\t\t\t},\r\n\t\t\t\t\t\"unitPrice\": {\r\n\t\t\t\t\t\t\"value\": 18.5,\r\n\t\t\t\t\t\t\"currency\": \"GBP\"\r\n\t\t\t\t\t},\r\n\t\t\t\t\t\"totalVat\": {\r\n\t\t\t\t\t\t\"value\": 37.0,\r\n\t\t\t\t\t\t\"currency\": \"GBP\"\r\n\t\t\t\t\t},\r\n\t\t\t\t\t\"totalPrice\": {\r\n\t\t\t\t\t\t\"value\": 212.0,\r\n\t\t\t\t\t\t\"currency\": \"GBP\"\r\n\t\t\t\t\t}\r\n\t\t\t\t}],\r\n\t\t\t\t\"minimumOrderValue\": {\r\n\t\t\t\t\t\"value\": 70.0,\r\n\t\t\t\t\t\"currency\": \"GBP\"\r\n\t\t\t\t},\r\n\t\t\t\t\"promotion\": {\r\n\t\t\t\t\t\"deductionAmount\": {\r\n\t\t\t\t\t\t\"value\": 10.0,\r\n\t\t\t\t\t\t\"currency\": \"GBP\"\r\n\t\t\t\t\t},\r\n\t\t\t\t\t\"code\": \"WELCOME10\",\r\n\t\t\t\t\t\"description\": \"\u00A310 off, test discount\",\r\n\t\t\t\t\t\"image\": {\r\n\t\t\t\t\t\t\"rel\": \"rel\",\r\n\t\t\t\t\t\t\"href\": \"http:\/\/whatever\",\r\n\t\t\t\t\t\t\"type\": \"image\"\r\n\t\t\t\t\t}\r\n\t\t\t\t}\r\n\t\t\t},\r\n\t\t\t\"invoiceNumber\": 233,\r\n\t\t\t\"paymentReference\": 123456,\r\n\t\t\t\"createdDate\": \"2016-10-26T13:03:04.396Z\",\r\n\t\t\t\"deliveryDate\": \"2016-10-27T09:00:00Z\",\r\n\t\t\t\"deliveryAddress\": {\r\n\t\t\t\t\"companyName\": \"OfficeServe\",\r\n\t\t\t\t\"deliverTo\": \"Nico\",\r\n\t\t\t\t\"address\": {\r\n\t\t\t\t\t\"addressLine1\": \"32A Denison Road\",\r\n\t\t\t\t\t\"postCode\": \"SW19 2DH\",\r\n\t\t\t\t\t\"city\": \"London\"\r\n\t\t\t\t}\r\n\t\t\t},\r\n\t\t\t\"invoiceAddress\": {\r\n\t\t\t\t\"companyName\": \"OfficeServe\",\r\n\t\t\t\t\"fullName\": \"Nico Cavallo\",\r\n\t\t\t\t\"address\": {\r\n\t\t\t\t\t\"addressLine1\": \"5 cheapSide, officeserveasda\",\r\n\t\t\t\t\t\"addressLine2\": \"cheapside, St pauls\",\r\n\t\t\t\t\t\"postCode\": \"EC2V 6AA\",\r\n\t\t\t\t\t\"city\": \"London\"\r\n\t\t\t\t}\r\n\t\t\t},\r\n\t\t\t\"telephoneNum\": \"07444392205\",\r\n\t\t\t\"orderStatus\": {\r\n\t\t\t\t\"status\": \"Pending\"\r\n\t\t\t},\r\n\t\t\t\"tableware\": 75,\r\n\t\t\t\"tablewareType\": \"TablewareNapkins\",\r\n\t\t\t\"paymentMethod\": {\r\n\t\t\t\t\"id\": \"97464efb-7de1-4193-b72e-8f822540b6dc\",\r\n\t\t\t\t\"paymentType\": \"CREDIT_CARD\",\r\n\t\t\t\t\"isDefault\": false,\r\n\t\t\t\t\"cardPayment\": {\r\n\t\t\t\t\t\"token\": \"QbKbsg3tgSl96Kj9vfnhUjtoaHV\",\r\n\t\t\t\t\t\"cardType\": \"MASTERCARD\",\r\n\t\t\t\t\t\"truncatedCardRep\": \"XXXX-XXXX-XXXX-4444\"\r\n\t\t\t\t},\r\n\t\t\t\t\"label\": \"MASTERCARD-XXXX-XXXX-XXXX-4444\",\r\n\t\t\t\t\"invoiceAddress\": {\r\n\t\t\t\t\t\"companyName\": \"OfficeServe\",\r\n\t\t\t\t\t\"fullName\": \"Nico Cavallo\",\r\n\t\t\t\t\t\"address\": {\r\n\t\t\t\t\t\t\"addressLine1\": \"5 cheapSide, officeserveasda\",\r\n\t\t\t\t\t\t\"addressLine2\": \"cheapside, St pauls\",\r\n\t\t\t\t\t\t\"postCode\": \"EC2V 6AA\",\r\n\t\t\t\t\t\t\"city\": \"London\"\r\n\t\t\t\t\t}\r\n\t\t\t\t}\r\n\t\t\t},\r\n\t\t\t\"orderReference\": \"KYM-9251234\",\r\n\t\t\t\"deliverySlot\": \" 09:00 to 11:00\",\r\n\t\t\t\"isCancellable\": true,\r\n\t\t\t\"cutOffTime\": \"2016-10-27T09:00:00Z\"\r\n\t\t},\r\n\t\t\"user\": {\r\n\t\t\t\"name\": \"jopo\",\r\n\t\t\t\"username\": \"jopo12\",\r\n\t\t\t\"email\": \"n.cavallo@officeserve.com\"\r\n\t\t}\r\n\t}]\r\n}",
      |	"Timestamp": "2016-11-03T09:27:13.846Z",
      |	"SignatureVersion": "1",
      |	"Signature": "RVFck9daL4I+7TGYv+aii1v0S6HcMd5S4XEXiDGbqAn1hYd97mL7+3FgGvQjJFN53IYG9rd1jWT2KEUD/8ADY9338MwuqT0BTO5gofT11509pbieyVDTCx/UUSkRxCX7rI4n+jhvcsZjJ1p2jMu5TmzInJF7Ho5VDlO/OCFmufgnTe1+9xkUIIggOtL2Yb8++ArN+KX9Gpj5K8xZliW9FQo5ASfjBZMXU1b6Ikif5yUQKeUXczOwdOzrFdOdYpQZ3CquEY75poJaVPVILtxRtNMZWkFNKy6oYLagXkUsYPlhIeQQ2BM16kiSbYAplInAGR/Cc2n9VOwCfyjMXc3Z/A==",
      |	"SigningCertURL": "https://sns.eu-west-1.amazonaws.com/SimpleNotificationService-b95095beb82e8f6a046b3aafc7f4149a.pem",
      |	"UnsubscribeURL": "https://sns.eu-west-1.amazonaws.com/?Action=Unsubscribe&SubscriptionArn=arn:aws:sns:eu-west-1:064345613152:dev_orders:0b88b5ec-a0b2-42af-b673-160d2d9dc72f"
      |}""".stripMargin



  val orderMessageRep = OrderMessageRep(
    order = OrderRep(
      id = "56a6b54c-977f-4c79-91be-abf1431c5fb5",
      userId = "us-east-1:f18de1da-8e98-4e94-b309-b7ffec6a5",
      basket = BasketRep(
        1,
        Price(185.0, "GBP"),
        Price(38.6, "GBP"),
        Some(PromoRep(
          deductionAmount = Price(10.0, "GBP"),
          code = "WELCOME10",
          image = LinkRep("rel", "http://whatever", "image"),
          description = "£10 off, test discount"
        )),
        Price(8.0, "GBP"),
        None,
        Price(231.6, "GBP"),
        Set(BasketItemRep(
          productId = "70adde14-bf53-410f-a08c-0def14353c8e",
          name = "Classic Comfort Breakfast",
          productCode = "TT500201",
          quantity = 10,
          price = Price(185.0, "GBP"),
          discountedPrice = None,
          unitPrice = Price(18.5, "GBP"),
          totalVat = Price(37.0, "GBP"),
          totalPrice = Price(212.0, "GBP"))),
        minimumOrderValue = Price(70.0, "GBP")
      ),
      invoiceNumber = Some(233),
      paymentReference = Some("123456"),
      createdDate = ZonedDateTime.parse("2016-10-26T13:03:04.396Z"),
      deliveryDate = Some(ZonedDateTime.parse("2016-10-27T09:00Z")),
      deliveryAddress = Some(DeliveryAddressRep(Some("OfficeServe"), "Nico", AddressRep("32A Denison Road", None, None, "SW19 2DH", "London", None, None))),
      invoiceAddress = Some(InvoiceAddressRep(Some("OfficeServe"), "Nico Cavallo", AddressRep("5 cheapSide, officeserveasda", Some("cheapside, St pauls"), None, "EC2V 6AA", "London", None, None))),
      telephoneNum = Some("07444392205"),
      orderStatus = OrderStatusRep("Pending"),
      tableware = 75,
      tablewareType = "TablewareNapkins",
      paymentMethod = Some(PaymentMethodRep(
        "97464efb-7de1-4193-b72e-8f822540b6dc",
        "CREDIT_CARD",
        false,
        Some(CardPaymentRep("QbKbsg3tgSl96Kj9vfnhUjtoaHV", "MASTERCARD", "XXXX-XXXX-XXXX-4444")),
        "MASTERCARD-XXXX-XXXX-XXXX-4444",
        InvoiceAddressRep(Some("OfficeServe"), "Nico Cavallo", AddressRep("5 cheapSide, officeserveasda", Some("cheapside, St pauls"), None, "EC2V 6AA", "London", None, None))
      )),
      orderReference = "KYM-9251234",
      deliverySlot = Some(" 09:00 to 11:00"),
      isCancellable = true,
      cutOffTime = Some(ZonedDateTime.parse("2016-10-27T09:00Z"))
    ),
    user = UserRep("jopo", "jopo12", "n.cavallo@officeserve.com")
  )

  val emailMessage = Event(SendEmail, Set(EmailMessage(
    from = "from@officeserve.com",
    to = Set("to1@officeserve.com", "to2@officeserve.com"),
    cc = Set("cc1@officeserve.com", "cc2@officeserve.com"),
    bcc = Set("bcc1@officeserve.com", "bcc2@officeserve.com"),
    subject = "email subject",
    body = "email body",
    attachments = Set(new URL("http://attachment.com")),
    format = Html
  )))
  val emailJson =
    """{
      |  "eventType": "SendEmail",
      |  "entities": [{
      |    "from": "from@officeserve.com",
      |    "to": ["to1@officeserve.com", "to2@officeserve.com"],
      |    "cc": ["cc1@officeserve.com", "cc2@officeserve.com"],
      |    "bcc": ["bcc1@officeserve.com", "bcc2@officeserve.com"],
      |    "subject": "email subject",
      |    "body": "email body",
      |    "format": "html",
      |    "attachments": ["http://attachment.com"]
      |  }]
      |}""".stripMargin

  val cutOffProcessOrdersJson = jsonEvent("CutOffTimeProcessOrders")
  val purchaseCompleteJson = jsonEvent("PurchaseComplete")
  val storeOrderssJson = jsonEvent("StoreOrders")
  val sendOrderDocumentsJson = jsonEvent("SendOrderDocuments")


  def jsonEvent(eventType: String) =
    s"""
       |{
       |	"eventType": "$eventType",
       |	"entities": [{
       |		"order": {
       |			"id": "56a6b54c-977f-4c79-91be-abf1431c5fb5",
       |			"userId": "us-east-1:f18de1da-8e98-4e94-b309-b7ffec6a5",
       |			"basket": {
       |				"itemsCount": 1,
       |				"totalPrice": {
       |					"value": 185.0,
       |					"currency": "GBP"
       |				},
       |				"totalVAT": {
       |					"value": 38.6,
       |					"currency": "GBP"
       |				},
       |				"deliveryCharge": {
       |					"value": 8.0,
       |					"currency": "GBP"
       |				},
       |				"grandTotal": {
       |					"value": 231.6,
       |					"currency": "GBP"
       |				},
       |				"items": [{
       |					"productId": "70adde14-bf53-410f-a08c-0def14353c8e",
       |					"name": "Classic Comfort Breakfast",
       |					"productCode": "TT500201",
       |					"quantity": 10,
       |					"price": {
       |						"value": 185.0,
       |						"currency": "GBP"
       |					},
       |					"unitPrice": {
       |						"value": 18.5,
       |						"currency": "GBP"
       |					},
       |					"totalVat": {
       |						"value": 37.0,
       |						"currency": "GBP"
       |					},
       |					"totalPrice": {
       |						"value": 212.0,
       |						"currency": "GBP"
       |					}
       |				}],
       |        "minimumOrderValue": {
       |					"value": 70.0,
       |					"currency": "GBP"
       |				},
       |        "promotion": {
       |          "deductionAmount": {
       |				  	"value": 10.0,
       |					  "currency": "GBP"
       |				  },
       |          "code": "WELCOME10",
       |          "description": "£10 off, test discount",
       |          "image": {
       |            "rel": "rel",
       |            "href": "http://whatever",
       |            "type": "image"
       |          }
       |        }
       |			},
       |			"invoiceNumber": 233,
       |      "paymentReference": 123456,
       |			"createdDate": "2016-10-26T13:03:04.396Z",
       |			"deliveryDate": "2016-10-27T09:00:00Z",
       |			"deliveryAddress": {
       |        "companyName": "OfficeServe",
       |				"deliverTo": "Nico",
       |				"address": {
       |					"addressLine1": "32A Denison Road",
       |					"postCode": "SW19 2DH",
       |					"city": "London"
       |				}
       |			},
       |			"invoiceAddress": {
       |        "companyName": "OfficeServe",
       |				"fullName": "Nico Cavallo",
       |				"address": {
       |					"addressLine1": "5 cheapSide, officeserveasda",
       |					"addressLine2": "cheapside, St pauls",
       |					"postCode": "EC2V 6AA",
       |					"city": "London"
       |				}
       |			},
       |			"telephoneNum": "07444392205",
       |			"orderStatus": {
       |				"status": "Pending"
       |			},
       |			"tableware": 75,
       |      "tablewareType": "TablewareNapkins",
       |			"paymentMethod": {
       |				"id": "97464efb-7de1-4193-b72e-8f822540b6dc",
       |				"paymentType": "CREDIT_CARD",
       |				"isDefault": false,
       |				"cardPayment": {
       |					"token": "QbKbsg3tgSl96Kj9vfnhUjtoaHV",
       |					"cardType": "MASTERCARD",
       |					"truncatedCardRep": "XXXX-XXXX-XXXX-4444"
       |				},
       |				"label": "MASTERCARD-XXXX-XXXX-XXXX-4444",
       |				"invoiceAddress": {
       |          "companyName": "OfficeServe",
       |					"fullName": "Nico Cavallo",
       |					"address": {
       |						"addressLine1": "5 cheapSide, officeserveasda",
       |						"addressLine2": "cheapside, St pauls",
       |						"postCode": "EC2V 6AA",
       |						"city": "London"
       |					}
       |				}
       |			},
       |			"orderReference": "KYM-9251234",
       |			"deliverySlot": " 09:00 to 11:00",
       |			"isCancellable": true,
       |      "cutOffTime": "2016-10-27T09:00:00Z"
       |		},
       |		"user": {
       |			"name": "jopo",
       |			"username": "jopo12",
       |			"email": "n.cavallo@officeserve.com"
       |		}
       |	}]
       |}
    """.stripMargin

}
