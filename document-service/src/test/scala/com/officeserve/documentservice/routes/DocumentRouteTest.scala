package com.officeserve.documentservice.routes

import java.io.ByteArrayInputStream
import java.net.URL

import akka.http.scaladsl.model.{ContentTypes, StatusCodes, Uri}
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.officeserve.documentservice.marshallers.JsonSupport
import com.officeserve.documentservice.models._
import com.officeserve.documentservice.services.DocumentService
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSpec, Matchers}
import org.mockito.Mockito._
import org.mockito.ArgumentMatchers._

import scala.concurrent.Future
import scala.collection.immutable.Seq

class DocumentRouteTest extends FunSpec with Matchers with ScalatestRouteTest with MockitoSugar with DocumentRouteFixtures with JsonSupport {

  describe("DocumentRoute") {


    describe("POST") {
      describe("/documents/invoice/pdf") {
        val documentService: DocumentService = mock[DocumentService]
        val documentRoute = new DocumentRoute(documentService)
        when(documentService.generateInvoicePdf(any[Order])) thenReturn Future.successful(fakeUrl)
        it("should return the url of the generated invoice in the location header") {
          Post("/documents/invoice/pdf", order) ~> documentRoute ~> check {
            status shouldBe StatusCodes.Created
            headers should contain(Location(Uri(fakeUrl.toURI.toString)))
            verify(documentService, times(1)).generateInvoicePdf(order)
          }
        }
      }
      describe("/documents/receipt/pdf") {
        val documentService: DocumentService = mock[DocumentService]
        val documentRoute = new DocumentRoute(documentService)
        when(documentService.generateReceiptPdf(any[Order])) thenReturn Future.successful(fakeUrl)
        it("should return the url of the generated receipt in the location header") {
          Post("/documents/receipt/pdf", order) ~> documentRoute ~> check {
            status shouldBe StatusCodes.Created
            headers should contain(Location(Uri(fakeUrl.toURI.toString)))
            verify(documentService, times(1)).generateReceiptPdf(order)
          }
        }
      }
      describe("/documents/delivery-note/pdf") {
        val documentService: DocumentService = mock[DocumentService]
        val documentRoute = new DocumentRoute(documentService)
        when(documentService.generateDeliveryNotePdf(any[Order])) thenReturn Future.successful(fakeUrl)
        it("should return the url of the generated delivery note in the location header") {
          Post("/documents/delivery-note/pdf", order) ~> documentRoute ~> check {
            status shouldBe StatusCodes.Created
            headers should contain(Location(Uri(fakeUrl.toURI.toString)))
            verify(documentService, times(1)).generateDeliveryNotePdf(order)
          }
        }
      }
      describe("/documents/delivery-manifest/pdf") {
        val documentService: DocumentService = mock[DocumentService]
        val documentRoute = new DocumentRoute(documentService)
        when(documentService.generateDeliveryManifestPdf(any[Seq[Order]])) thenReturn Future.successful(fakeUrl)
        it("should return the url of the generated delivery manifest in the location header") {
          Post("/documents/delivery-manifest/pdf", Seq(order, order, order)) ~> documentRoute ~> check {
            status shouldBe StatusCodes.Created
            headers should contain(Location(Uri(fakeUrl.toURI.toString)))
            verify(documentService, times(1)).generateDeliveryManifestPdf(Seq(order, order, order))
          }
        }
      }
      describe("/documents/product-report/pdf") {
        val documentService: DocumentService = mock[DocumentService]
        val documentRoute = new DocumentRoute(documentService)
        when(documentService.generateProductReportPdf(any[ProductReport])) thenReturn Future.successful(fakeUrl)
        it("should return the url of the generated delivery manifest in the location header") {
          Post("/documents/product-report/pdf", productReport) ~> documentRoute ~> check {
            status shouldBe StatusCodes.Created
            headers should contain(Location(Uri(fakeUrl.toURI.toString)))
            verify(documentService, times(1)).generateProductReportPdf(productReport)
          }
        }
      }
      describe("/documents/invoice/email") {
        val documentService: DocumentService = mock[DocumentService]
        val documentRoute = new DocumentRoute(documentService)
        when(documentService.generateInvoiceEmail(any[Order])) thenReturn Future.successful(htmlInputStream)
        it("should return the generated email in the message body") {
          Post("/documents/invoice/email", order) ~> documentRoute ~> check {
            status shouldBe StatusCodes.Created
            contentType shouldBe ContentTypes.`text/html(UTF-8)`
            responseAs[String] shouldBe html
            verify(documentService, times(1)).generateInvoiceEmail(order)
          }
        }
      }
      describe("/documents/receipt/email") {
        val documentService: DocumentService = mock[DocumentService]
        val documentRoute = new DocumentRoute(documentService)
        when(documentService.generateReceiptEmail(any[Order])) thenReturn Future.successful(htmlInputStream)
        it("should return the generated email in the message body") {
          Post("/documents/receipt/email", order) ~> documentRoute ~> check {
            status shouldBe StatusCodes.Created
            contentType shouldBe ContentTypes.`text/html(UTF-8)`
            responseAs[String] shouldBe html
            verify(documentService, times(1)).generateReceiptEmail(order)
          }
        }
      }
      describe("/documents/cancellation/email") {
        val documentService: DocumentService = mock[DocumentService]
        val documentRoute = new DocumentRoute(documentService)
        when(documentService.generateCancellationEmail(any[Order])) thenReturn Future.successful(htmlInputStream)
        it("should return the generated email in the message body") {
          Post("/documents/cancellation/email", order) ~> documentRoute ~> check {
            status shouldBe StatusCodes.Created
            contentType shouldBe ContentTypes.`text/html(UTF-8)`
            responseAs[String] shouldBe html
            verify(documentService, times(1)).generateCancellationEmail(order)
          }
        }
      }
      describe("/documents/delivery-note/email") {
        val documentService: DocumentService = mock[DocumentService]
        val documentRoute = new DocumentRoute(documentService)
        when(documentService.generateDeliveryNoteEmail) thenReturn Future.successful(htmlInputStream)
        it("should return the generated email in the message body") {
          Post("/documents/delivery-note/email", order) ~> documentRoute ~> check {
            status shouldBe StatusCodes.Created
            contentType shouldBe ContentTypes.`text/html(UTF-8)`
            responseAs[String] shouldBe html
            verify(documentService, times(1)).generateDeliveryNoteEmail
          }
        }
      }
      describe("/documents/product-report/email") {
        val documentService: DocumentService = mock[DocumentService]
        val documentRoute = new DocumentRoute(documentService)
        when(documentService.generateProductReportEmail) thenReturn Future.successful(htmlInputStream)
        it("should return the generated email in the message body") {
          Post("/documents/product-report/email", order) ~> documentRoute ~> check {
            status shouldBe StatusCodes.Created
            contentType shouldBe ContentTypes.`text/html(UTF-8)`
            responseAs[String] shouldBe html
            verify(documentService, times(1)).generateProductReportEmail
          }
        }
      }
    }
  }

}

trait DocumentRouteFixtures {
  val fakeUrl = new URL("http://fake.s3.com/document.pdf")

  val address = Address("Music Building", Some("Rock Rd"), None, "R0 CK", "London", None)
  val invoiceTo = InvoiceTo("Frank Zappa", address, "123456", "frank.zappa@gmail.com")

  val invoiceDetails = InvoiceDetails("01/01/2016", "1234", None, "Jimmy Page", Some("09876"), Some("123"), "On Account", Some("Carrier Pigeon"))
  val deliveryAddress = DeliveryAddress(None, "John Petrucci", address)

  val deliveryDate = Some("Fri 17 Jun 2016")
  val deliverySlot = "09:00-11:00"
  val basket = Basket("£1", "£1", "£1", "£1", "£1", Some(Promo("25% first order discount", "£1")), List(Item("BLT", "BG123-POP", "1", "£1", "£1", "£1", "£1")))
  val order = Order(invoiceTo, invoiceDetails, deliveryAddress, deliveryDate, deliverySlot, basket)

  private val productReportItems = List(
    ProductReportItem("AB-123", "Classic Sandwich Selection", "12", "+3", "15","Fri 17 Jun 2016"),
    ProductReportItem("AB-124", "Mediterranean Roasted Vegetables & Pasta", "9", "+2", "11","Fri 17 Jun 2016"),
    ProductReportItem("AB-125", "Middle Eastern-style Dips with Crudités", "15", "-3", "12","Fri 17 Jun 2016")
  )
  val productReport = ProductReport("Thu 16 Jun 2016 10:59", productReportItems)

  val html =
    <html>
      <body>
        <div>
          <div>Invoice To</div>
          <div>Great Company Ltd</div>
          <div>Beautiful Address Rd</div>
        </div>
      </body>
    </html>.toString()

  def htmlInputStream = new ByteArrayInputStream(html.getBytes)
}
