package com.officeserve.reportservice.services

import java.time._

import com.officeserve.basketservice.web._
import com.officeserve.reportservice.models.orderlog.OrderLog
import com.officeserve.reportservice.models.productreport._
import com.officeserve.reportservice.repositories.{OrderLogRepository, ProductReportLogRepository}
import officeserve.commons.spray.webutils.Price
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{AsyncFunSpec, Matchers}

import scala.concurrent.Future

class ProductReportServiceTest extends AsyncFunSpec with Matchers with MockitoSugar with ProductReportServiceFixtures {
  describe("ProductReportService") {

    val clock = Clock.fixed(Instant.parse("2016-10-26T10:15:30.00Z"), ZoneId.systemDefault())
    val clockDelivery = Clock.fixed(Instant.parse("2016-10-27T10:15:30.00Z"), ZoneId.systemDefault())


    describe("when storing an order log") {
      val productReportLogRepository = mock[ProductReportLogRepository]
          val pendingOrder = orderMessageRep(clockDelivery, "orderId1", "Pending")

      orderMessageRepToOrderLog(LocalDate.now(clock), pendingOrder)

      val orderLog = OrderLog(LocalDate.now(clock), s"Pending_12345", pendingOrder)

      describe("if there are no issues") {
        val orderLogRepository = mock[OrderLogRepository]
        when(orderLogRepository.put(orderLog)) thenReturn Future.successful({})
        val productReportService = new ProductReportServiceImpl(clock, orderLogRepository, productReportLogRepository)
        it("should put it in dynamo") {
          productReportService.storeOrderLog(orderLog).map { result =>
            verify(orderLogRepository, times(1)).put(orderLog)
            result shouldBe {}
          }
        }
      }
      describe("if there are issues with dynamo") {
        val orderLogRepository = mock[OrderLogRepository]
        when(orderLogRepository.put(orderLog)) thenReturn Future.failed(new Exception("test-dynamo-exception"))
        val productReportService = new ProductReportServiceImpl(clock, orderLogRepository, productReportLogRepository)
        it("should put it in dynamo") {
          recoverToSucceededIf[Exception](
            productReportService.storeOrderLog(orderLog)
          )
        }
      }
    }
    describe("when generating a product report") {

      val cutOffDate = LocalDate.now(clock)
      val deliveryZoneDateTime = ZonedDateTime.now(clockDelivery)
      describe("and it's not the first generation") {

        describe("and the previous report doesn't contain any of the current products") {
          val productReportLogRepository = mock[ProductReportLogRepository]

          val productReportData = Map[ProductId, ProductReportEntry](
            "1" -> ProductReportEntry("1", "TT000001", "Classic Chicken Sandwich", 0, 5, deliveryZoneDateTime),
            "2" -> ProductReportEntry("2", "TT000002", "Tomatoes soup", 0, 3, deliveryZoneDateTime),
            "3" -> ProductReportEntry("3", "TT000003", "Pork belly", 0, 6, deliveryZoneDateTime),
            "4" -> ProductReportEntry("4", "TT000004", "Roast hog", 0, 9, deliveryZoneDateTime)
          )
          val productReport = ProductReport(cutOffDate, ZonedDateTime.now(clock), productReportData)
          when(productReportLogRepository.retrieveLatest(cutOffDate)) thenReturn Future.successful(Some(productReport))
          when(productReportLogRepository.put(ArgumentMatchers.any[ProductReport])) thenReturn Future.successful({})

          val orderLogRepository = mock[OrderLogRepository]
          val orderLogs = orderMessageRepToOrderLog(cutOffDate,
            orderMessageRep(clockDelivery, "orderId0", "Pending",
              FakeProduct("1", "TT000001", "Classic Chicken Sandwich", 5),
              FakeProduct("2", "TT000002", "Tomatoes soup", 3),
              FakeProduct("3", "TT000003", "Pork belly", 6),
              FakeProduct("4", "TT000004", "Roast hog", 9)
            ),
            orderMessageRep(clockDelivery, "orderId1", "Pending", FakeProduct("5", "TT000005", "Frog legs", 10)),
            orderMessageRep(clockDelivery, "orderId2", "Pending", FakeProduct("6", "TT000006", "Pork's ear", 3)),
            orderMessageRep(clockDelivery, "orderId1", "Cancelled", FakeProduct("5", "TT000005", "Frog legs", 10))
          )

          when(orderLogRepository.query(cutOffDate)) thenReturn Future.successful(orderLogs)


          val expectedProductReportData = Map[ProductId, ProductReportEntry](
            "1" -> ProductReportEntry("1", "TT000001", "Classic Chicken Sandwich", 5, 5, deliveryZoneDateTime),
            "2" -> ProductReportEntry("2", "TT000002", "Tomatoes soup", 3, 3, deliveryZoneDateTime),
            "3" -> ProductReportEntry("3", "TT000003", "Pork belly", 6, 6, deliveryZoneDateTime),
            "4" -> ProductReportEntry("4", "TT000004", "Roast hog", 9, 9, deliveryZoneDateTime),
            "5" -> ProductReportEntry("5", "TT000005", "Frog legs", 0, 0, deliveryZoneDateTime),
            "6" -> ProductReportEntry("6", "TT000006", "Pork's ear", 0, 3, deliveryZoneDateTime)
          )
          val expectedReport = productReport.copy(report = expectedProductReportData)

          val productReportService = new ProductReportServiceImpl(clock, orderLogRepository, productReportLogRepository)

          it("should append the previous results and aggregate by productId and store the report in dynamo") {
            productReportService.generate.map { result =>
              verify(productReportLogRepository).put(expectedReport)
              result shouldBe expectedReport
            }
          }
        }
        describe("and the previous report contains some of the current products") {
          val productReportLogRepository = mock[ProductReportLogRepository]

          val productReportData = Map[ProductId, ProductReportEntry](
            "1" -> ProductReportEntry("1", "TT000001", "Classic Chicken Sandwich", 0, 5, deliveryZoneDateTime),
            "2" -> ProductReportEntry("2", "TT000002", "Tomatoes soup", 0, 3, deliveryZoneDateTime),
            "3" -> ProductReportEntry("3", "TT000003", "Pork belly", 0, 6, deliveryZoneDateTime),
            "4" -> ProductReportEntry("4", "TT000004", "Roast hog", 0, 9, deliveryZoneDateTime)
          )
          val productReport = ProductReport(cutOffDate, ZonedDateTime.now(clock), productReportData)
          when(productReportLogRepository.retrieveLatest(cutOffDate)) thenReturn Future.successful(Some(productReport))
          when(productReportLogRepository.put(ArgumentMatchers.any[ProductReport])) thenReturn Future.successful({})

          val orderLogRepository = mock[OrderLogRepository]
          val orderLogs = orderMessageRepToOrderLog(cutOffDate,
            orderMessageRep(clockDelivery, "orderId0", "Pending",
              FakeProduct("1", "TT000001", "Classic Chicken Sandwich", 5),
              FakeProduct("2", "TT000002", "Tomatoes soup", 3),
              FakeProduct("3", "TT000003", "Pork belly", 6),
              FakeProduct("4", "TT000004", "Roast hog", 9)
            ),
            orderMessageRep(clockDelivery, "orderId1", "Pending", FakeProduct("5", "TT000005", "Frog legs", 10)),
            orderMessageRep(clockDelivery, "orderId2", "Pending", FakeProduct("1", "TT000001", "Classic Chicken Sandwich", 10)),
            orderMessageRep(clockDelivery, "orderId1", "Cancelled", FakeProduct("5", "TT000005", "Frog legs", 10))
          )

          when(orderLogRepository.query(cutOffDate)) thenReturn Future.successful(orderLogs)


          val expectedProductReportData = Map[ProductId, ProductReportEntry](
            "1" -> ProductReportEntry("1", "TT000001", "Classic Chicken Sandwich", 5, 15, deliveryZoneDateTime),
            "2" -> ProductReportEntry("2", "TT000002", "Tomatoes soup", 3, 3, deliveryZoneDateTime),
            "3" -> ProductReportEntry("3", "TT000003", "Pork belly", 6, 6, deliveryZoneDateTime),
            "4" -> ProductReportEntry("4", "TT000004", "Roast hog", 9, 9, deliveryZoneDateTime),
            "5" -> ProductReportEntry("5", "TT000005", "Frog legs", 0, 0, deliveryZoneDateTime)
          )
          val expectedReport = productReport.copy(report = expectedProductReportData)

          val productReportService = new ProductReportServiceImpl(clock, orderLogRepository, productReportLogRepository)
          it("should merge with the previous results and aggregate by productId and store the report in dynamo") {
            productReportService.generate.map { result =>
              verify(productReportLogRepository).put(expectedReport)
              result shouldBe expectedReport
            }
          }
        }
      }
    }
  }
}

trait ProductReportServiceFixtures {

  case class FakeProduct(productId: String, productCode: String, productName: String, quantity: Int)

  def orderMessageRepToOrderLog(cutOffDate: LocalDate, ordermessageReps: OrderMessageRep*): Seq[OrderLog] =
    ordermessageReps.map { o =>
      OrderLog(cutOffDate, o.order.orderStatus.status + "_" + o.order.id, o)
    }

  def orderMessageRepToProductReport(orderMessageRep: OrderMessageRep): ProductReportData =
    orderMessageRep.order.basket.items.foldLeft[ProductReportData](Map.empty) { (acc, curr) =>
      acc + (curr.productId -> ProductReportEntry(curr.productId, curr.productCode, curr.name, 0, curr.quantity, orderMessageRep.order.deliveryDate.get))
    }

  def orderMessageRep(clock: Clock, orderId: String, status: String, products: FakeProduct*) = OrderMessageRep(
    order = OrderRep(
      id = orderId,
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
        items = products.map { p =>
          BasketItemRep(
            productId = p.productId,
            name = p.productName,
            productCode = p.productCode,
            quantity = p.quantity,
            price = Price(185.0, "GBP"),
            discountedPrice = None,
            unitPrice = Price(18.5, "GBP"),
            totalVat = Price(37.0, "GBP"),
            totalPrice = Price(212.0, "GBP"))
        }.toSet,
        minimumOrderValue = Price(70.0, "GBP")
      ),
      invoiceNumber = Some(233),
      paymentReference = Some("123456"),
      createdDate = ZonedDateTime.parse("2016-10-26T13:03:04.396Z"),
      deliveryDate = Some(ZonedDateTime.now(clock)),
      deliveryAddress = Some(DeliveryAddressRep(Some("OfficeServe"), "Nico", AddressRep("32A Denison Road", None, None, "SW19 2DH", "London", None, None))),
      invoiceAddress = Some(InvoiceAddressRep(Some("OfficeServe"), "Nico Cavallo", AddressRep("5 cheapSide, officeserveasda", Some("cheapside, St pauls"), None, "EC2V 6AA", "London", None, None))),
      telephoneNum = Some("07444392205"),
      orderStatus = OrderStatusRep(status),
      tableware = 75,
      tablewareType = "NoTableware",
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
}
