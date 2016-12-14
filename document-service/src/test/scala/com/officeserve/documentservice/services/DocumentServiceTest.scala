package com.officeserve.documentservice.services

import java.io._
import java.net.URL
import java.util
import java.util.concurrent.atomic.AtomicInteger

import com.github.mustachejava.util.Node
import com.github.mustachejava.{Code, Mustache, MustacheFactory}
import com.officeserve.documentservice.models._
import com.officeserve.documentservice.services.PdfService.{Landscape, Orientation, Portrait}
import com.officeserve.documentservice.settings.DocumentSettings
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{AsyncFunSpec, Matchers}

import scala.concurrent.Future


class DocumentServiceTest extends AsyncFunSpec with Matchers with MockitoSugar with DocumentFixtures {

  describe("DocumentService") {
    val settings = DocumentSettings(
      templatesBucket = "testing-bucket",
      documentsBucket = "testing-bucket",
      invoicePdfTemplate = "invoice-pdf.mustache",
      receiptPdfTemplate = "receipt-pdf.mustache",
      deliveryNotePdfTemplate = "delivery_note-pdf.mustache",
      deliveryManifestPdfTemplate = "delivery_manifest-pdf.mustache",
      productReportPdfTemplate = "product_report-pdf.mustache",
      invoiceEmailTemplate = "invoice-email.mustache",
      receiptEmailTemplate = "receipt-email.mustache",
      cancellationEmailTemplate = "cancellation-email.mustache",
      deliveryNoteEmailTemplate = "delivery_note-email.mustache",
      productReportEmailTemplate = "product_report-email.mustache"
    )

    describe("when generating an invoice") {
      describe("pdf") {
        val s3Service = mock[S3Service]
        val mustacheFactory = mock[MustacheFactory]
        val mustache = mock[Mustache]

        val pdfService = mock[PdfService]
        val fileNameService = mock[FileNameService]

        val documentService = new DocumentServiceImpl(settings, s3Service, mustacheFactory, pdfService, fileNameService)

        when(s3Service.getObject(settings.templatesBucket, settings.invoicePdfTemplate)) thenReturn Future.successful(htmlInputStream)
        when(mustacheFactory.compile(any[Reader], ArgumentMatchers.eq(settings.invoicePdfTemplate))) thenReturn mustache

        when(pdfService.generate(any[InputStream], any[Orientation])) thenReturn fakePdf

        when(fileNameService.generatePath(order.invoiceDetails.invoiceNumber + "-invoice.pdf")) thenReturn "2016/10/07/" + order.invoiceDetails.invoiceNumber + "-invoice.pdf"
        when(s3Service.putObject(anyString(), anyString(), ArgumentMatchers.eq(fakePdf))) thenReturn Future(fakeUrl)

        it("should generate it") {
          val data = Map("data" -> order)

          documentService.generateInvoicePdf(order) map { url =>
            val inOrderGenerateDocument = Mockito.inOrder(mustacheFactory, mustache)
            inOrderGenerateDocument.verify(mustacheFactory, times(1)).compile(any[Reader], ArgumentMatchers.eq(settings.invoicePdfTemplate))
            inOrderGenerateDocument.verify(mustache).execute(any[Writer], ArgumentMatchers.eq(data))

            val inOrder = Mockito.inOrder(pdfService, fileNameService, s3Service)
            inOrder.verify(s3Service, times(1)).getObject(settings.templatesBucket, settings.invoicePdfTemplate)
            inOrder.verify(pdfService, times(1)).generate(any[InputStream], ArgumentMatchers.eq(Portrait))
            inOrder.verify(fileNameService, times(1)).generatePath(order.invoiceDetails.invoiceNumber + "-invoice.pdf")
            inOrder.verify(s3Service, times(1)).putObject(ArgumentMatchers.eq(settings.documentsBucket), anyString(), ArgumentMatchers.eq(fakePdf))
            url shouldBe fakeUrl
          }
        }
      }
      describe("email") {
        val s3Service = mock[S3Service]
        val mustacheFactory = mock[MustacheFactory]
        val mustache = new MustacheStub(html)

        val pdfService = mock[PdfService]
        val fileNameService = mock[FileNameService]

        val documentService = new DocumentServiceImpl(settings, s3Service, mustacheFactory, pdfService, fileNameService)

        when(s3Service.getObject(settings.templatesBucket, settings.invoiceEmailTemplate)) thenReturn Future.successful(htmlInputStream)
        when(mustacheFactory.compile(any[Reader], ArgumentMatchers.eq(settings.invoiceEmailTemplate))) thenReturn mustache

        it("should generate it") {
          documentService.generateInvoiceEmail(order) map { htmlInputStream =>
            verify(mustacheFactory, times(1)).compile(any[Reader], ArgumentMatchers.eq(settings.invoiceEmailTemplate))
            streamToComparable(htmlInputStream) shouldBe html
          }
        }
      }
    }
    describe("when generating a receipt") {
      describe("pdf") {
        val s3Service = mock[S3Service]
        val mustacheFactory = mock[MustacheFactory]
        val mustache = mock[Mustache]

        val pdfService = mock[PdfService]
        val fileNameService = mock[FileNameService]

        val documentService = new DocumentServiceImpl(settings, s3Service, mustacheFactory, pdfService, fileNameService)

        when(s3Service.getObject(settings.templatesBucket, settings.receiptPdfTemplate)) thenReturn Future.successful(htmlInputStream)
        when(mustacheFactory.compile(any[Reader], ArgumentMatchers.eq(settings.receiptPdfTemplate))) thenReturn mustache

        when(pdfService.generate(any[InputStream], any[Orientation])) thenReturn fakePdf

        when(fileNameService.generatePath(order.invoiceDetails.invoiceNumber + "-receipt.pdf")) thenReturn "2016/10/07/" + order.invoiceDetails.invoiceNumber + "-receipt.pdf"
        when(s3Service.putObject(anyString(), anyString(), ArgumentMatchers.eq(fakePdf))) thenReturn Future(fakeUrl)

        it("should generate it") {
          val data = Map("data" -> order)

          documentService.generateReceiptPdf(order) map { url =>
            val inOrderGenerateDocument = Mockito.inOrder(mustacheFactory, mustache)
            inOrderGenerateDocument.verify(mustacheFactory, times(1)).compile(any[Reader], ArgumentMatchers.eq(settings.receiptPdfTemplate))
            inOrderGenerateDocument.verify(mustache).execute(any[Writer], ArgumentMatchers.eq(data))

            val inOrder = Mockito.inOrder(pdfService, fileNameService, s3Service)
            inOrder.verify(s3Service, times(1)).getObject(settings.templatesBucket, settings.receiptPdfTemplate)
            inOrder.verify(pdfService, times(1)).generate(any[InputStream], ArgumentMatchers.eq(Portrait))
            inOrder.verify(fileNameService, times(1)).generatePath(order.invoiceDetails.invoiceNumber + "-receipt.pdf")
            inOrder.verify(s3Service, times(1)).putObject(ArgumentMatchers.eq(settings.documentsBucket), anyString(), ArgumentMatchers.eq(fakePdf))
            url shouldBe fakeUrl
          }
        }
      }
      describe("email") {
        val s3Service = mock[S3Service]
        val mustacheFactory = mock[MustacheFactory]
        val mustache = new MustacheStub(html)

        val pdfService = mock[PdfService]
        val fileNameService = mock[FileNameService]

        val documentService = new DocumentServiceImpl(settings, s3Service, mustacheFactory, pdfService, fileNameService)

        when(s3Service.getObject(settings.templatesBucket, settings.receiptEmailTemplate)) thenReturn Future.successful(htmlInputStream)
        when(mustacheFactory.compile(any[Reader], ArgumentMatchers.eq(settings.receiptEmailTemplate))) thenReturn mustache


        it("should generate it") {
          documentService.generateReceiptEmail(order) map { htmlInputStream =>
            verify(mustacheFactory, times(1)).compile(any[Reader], ArgumentMatchers.eq(settings.receiptEmailTemplate))
            streamToComparable(htmlInputStream) shouldBe html
          }
        }
      }
    }
    describe("when generating a cancellation") {
      describe("email") {
        val s3Service = mock[S3Service]
        val mustacheFactory = mock[MustacheFactory]
        val mustache = new MustacheStub(html)

        val pdfService = mock[PdfService]
        val fileNameService = mock[FileNameService]

        val documentService = new DocumentServiceImpl(settings, s3Service, mustacheFactory, pdfService, fileNameService)

        when(s3Service.getObject(settings.templatesBucket, settings.cancellationEmailTemplate)) thenReturn Future.successful(htmlInputStream)
        when(mustacheFactory.compile(any[Reader], ArgumentMatchers.eq(settings.cancellationEmailTemplate))) thenReturn mustache


        it("should generate it") {
          documentService.generateCancellationEmail(order) map { htmlInputStream =>
            verify(mustacheFactory, times(1)).compile(any[Reader], ArgumentMatchers.eq(settings.cancellationEmailTemplate))
            streamToComparable(htmlInputStream) shouldBe html
          }
        }
      }
    }
    describe("when generating a delivery note") {
      describe("pdf") {
        val s3Service = mock[S3Service]
        val mustacheFactory = mock[MustacheFactory]
        val mustache = mock[Mustache]

        val pdfService = mock[PdfService]
        val fileNameService = mock[FileNameService]

        val documentService = new DocumentServiceImpl(settings, s3Service, mustacheFactory, pdfService, fileNameService)

        when(s3Service.getObject(settings.templatesBucket, settings.deliveryNotePdfTemplate)) thenReturn Future.successful(htmlInputStream)
        when(mustacheFactory.compile(any[Reader], ArgumentMatchers.eq(settings.deliveryNotePdfTemplate))) thenReturn mustache

        when(pdfService.generate(any[InputStream], any[Orientation])) thenReturn fakePdf

        when(fileNameService.generatePath(order.invoiceDetails.invoiceNumber + "-delivery_note.pdf")) thenReturn "2016/10/07/" + order.invoiceDetails.invoiceNumber + "-delivery_note.pdf"
        when(s3Service.putObject(anyString(), anyString(), ArgumentMatchers.eq(fakePdf))) thenReturn Future(fakeUrl)

        it("should generate it") {
          val data = Map("data" -> order)

          documentService.generateDeliveryNotePdf(order) map { url =>
            val inOrderGenerateDocument = Mockito.inOrder(mustacheFactory, mustache)
            inOrderGenerateDocument.verify(mustacheFactory, times(1)).compile(any[Reader], ArgumentMatchers.eq(settings.deliveryNotePdfTemplate))
            inOrderGenerateDocument.verify(mustache).execute(any[Writer], ArgumentMatchers.eq(data))

            val inOrder = Mockito.inOrder(pdfService, fileNameService, s3Service)
            inOrder.verify(s3Service, times(1)).getObject(settings.templatesBucket, settings.deliveryNotePdfTemplate)
            inOrder.verify(pdfService, times(1)).generate(any[InputStream], ArgumentMatchers.eq(Portrait))
            inOrder.verify(fileNameService, times(1)).generatePath(order.invoiceDetails.invoiceNumber + "-delivery_note.pdf")
            inOrder.verify(s3Service, times(1)).putObject(ArgumentMatchers.eq(settings.documentsBucket), anyString(), ArgumentMatchers.eq(fakePdf))
            url shouldBe fakeUrl
          }
        }
      }
      describe("email") {
        val s3Service = mock[S3Service]
        val mustacheFactory = mock[MustacheFactory]

        val pdfService = mock[PdfService]
        val fileNameService = mock[FileNameService]

        val documentService = new DocumentServiceImpl(settings, s3Service, mustacheFactory, pdfService, fileNameService)

        when(s3Service.getObject(settings.templatesBucket, settings.deliveryNoteEmailTemplate)) thenReturn Future.successful(htmlInputStream)


        it("should generate it") {
          documentService.generateDeliveryNoteEmail map { htmlInputStream =>
            streamToComparable(htmlInputStream) shouldBe html
          }
        }
      }
    }
    describe("when generating a delivery manifest") {
      describe("pdf") {
        val s3Service = mock[S3Service]
        val mustacheFactory = mock[MustacheFactory]
        val mustache = mock[Mustache]

        val pdfService = mock[PdfService]
        val fileNameService = mock[FileNameService]

        val documentService = new DocumentServiceImpl(settings, s3Service, mustacheFactory, pdfService, fileNameService)

        when(s3Service.getObject(settings.templatesBucket, settings.deliveryManifestPdfTemplate)) thenReturn Future.successful(htmlInputStream)
        when(mustacheFactory.compile(any[Reader], ArgumentMatchers.eq(settings.deliveryManifestPdfTemplate))) thenReturn mustache

        when(pdfService.generate(any[InputStream], any[Orientation])) thenReturn fakePdf

        when(fileNameService.generatePath("delivery_manifest.pdf")) thenReturn "2016/10/07/delivery_manifest.pdf"
        when(s3Service.putObject(anyString(), anyString(), ArgumentMatchers.eq(fakePdf))) thenReturn Future(fakeUrl)

        it("should generate it") {
          val data = Map("data" -> List(order, order, order))

          documentService.generateDeliveryManifestPdf(List(order, order, order)).map { url =>
            val inOrderGenerateDocument = Mockito.inOrder(mustacheFactory, mustache)
            inOrderGenerateDocument.verify(mustacheFactory, times(1)).compile(any[Reader], ArgumentMatchers.eq(settings.deliveryManifestPdfTemplate))
            inOrderGenerateDocument.verify(mustache).execute(any[Writer], ArgumentMatchers.eq(data))

            val inOrder = Mockito.inOrder(pdfService, fileNameService, s3Service)
            inOrder.verify(s3Service, times(1)).getObject(settings.templatesBucket, settings.deliveryManifestPdfTemplate)
            inOrder.verify(pdfService, times(1)).generate(any[InputStream],ArgumentMatchers.eq(Landscape))
            inOrder.verify(fileNameService, times(1)).generatePath("delivery_manifest.pdf")
            inOrder.verify(s3Service, times(1)).putObject(ArgumentMatchers.eq(settings.documentsBucket), anyString(), ArgumentMatchers.eq(fakePdf))
            url shouldBe fakeUrl
          }
        }
      }
    }
    describe("when generating a product report") {
      describe("pdf") {
        val s3Service = mock[S3Service]
        val mustacheFactory = mock[MustacheFactory]
        val mustache = mock[Mustache]

        val pdfService = mock[PdfService]
        val fileNameService = mock[FileNameService]

        val documentService = new DocumentServiceImpl(settings, s3Service, mustacheFactory, pdfService, fileNameService)

        when(s3Service.getObject(settings.templatesBucket, settings.productReportPdfTemplate)) thenReturn Future.successful(htmlInputStream)
        when(mustacheFactory.compile(any[Reader], ArgumentMatchers.eq(settings.productReportPdfTemplate))) thenReturn mustache

        when(pdfService.generate(any[InputStream], any[Orientation])) thenReturn fakePdf

        when(fileNameService.generatePath(s"${productReport.date}-product_report.pdf")) thenReturn s"2016/10/07/${productReport.date}-product_report.pdf"
        when(s3Service.putObject(anyString(), anyString(), ArgumentMatchers.eq(fakePdf))) thenReturn Future(fakeUrl)

        it("should generate it") {
          val data = Map("data" -> productReport)

          documentService.generateProductReportPdf(productReport).map { url =>
            val inOrderGenerateDocument = Mockito.inOrder(mustacheFactory, mustache)
            inOrderGenerateDocument.verify(mustacheFactory, times(1)).compile(any[Reader], ArgumentMatchers.eq(settings.productReportPdfTemplate))
            inOrderGenerateDocument.verify(mustache).execute(any[Writer], ArgumentMatchers.eq(data))

            val inOrder = Mockito.inOrder(pdfService, fileNameService, s3Service)
            inOrder.verify(s3Service, times(1)).getObject(settings.templatesBucket, settings.productReportPdfTemplate)
            inOrder.verify(pdfService, times(1)).generate(any[InputStream],ArgumentMatchers.eq(Portrait))
            inOrder.verify(fileNameService, times(1)).generatePath(s"${productReport.date}-product_report.pdf")
            inOrder.verify(s3Service, times(1)).putObject(ArgumentMatchers.eq(settings.documentsBucket), anyString(), ArgumentMatchers.eq(fakePdf))
            url shouldBe fakeUrl
          }
        }
      }
      describe("email") {
        val s3Service = mock[S3Service]
        val mustacheFactory = mock[MustacheFactory]

        val pdfService = mock[PdfService]
        val fileNameService = mock[FileNameService]

        val documentService = new DocumentServiceImpl(settings, s3Service, mustacheFactory, pdfService, fileNameService)

        when(s3Service.getObject(settings.templatesBucket, settings.productReportEmailTemplate)) thenReturn Future.successful(htmlInputStream)


        it("should generate it") {
          documentService.generateProductReportEmail map { htmlInputStream =>
            streamToComparable(htmlInputStream) shouldBe html
          }
        }
      }
    }
  }

  def streamToComparable(is: InputStream): String =
    new String(Stream.continually(is.read).takeWhile(_ != -1).map(_.toByte).toArray)

}

trait DocumentFixtures {

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

  val fakePdf = new ByteArrayInputStream("FakePdf content".getBytes)

  val fakeUrl = new URL("http://fake.s3.com/document.pdf")

  class MustacheStub(expectedOutput: String) extends Mustache {
    override def init(): Unit = ???

    override def identity(writer: Writer): Unit = ???

    override def execute(writer: Writer, scope: scala.Any): Writer = {
      writer.write(expectedOutput)
      writer
    }

    override def execute(writer: Writer, scopes: util.List[AnyRef]): Writer = ???

    override def invert(text: String): Node = ???

    override def append(text: String): Unit = ???

    override def run(writer: Writer, scopes: util.List[AnyRef]): Writer = ???

    override def getCodes: Array[Code] = ???

    override def setCodes(codes: Array[Code]): Unit = ???

    override def getName: String = ???

    override def clone(seen: util.Set[Code]): AnyRef = ???

    override def invert(node: Node, text: String, position: AtomicInteger): Node = ???
  }

}
