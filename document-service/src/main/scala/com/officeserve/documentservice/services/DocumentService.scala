package com.officeserve.documentservice.services

import java.io._
import java.net.URL

import com.github.mustachejava.MustacheFactory
import com.officeserve.documentservice.models.{Order, ProductReport}
import com.officeserve.documentservice.services.PdfService.{Landscape, Orientation, Portrait}
import com.officeserve.documentservice.settings.DocumentSettings

import scala.concurrent.{ExecutionContext, Future}
import scala.collection.immutable.Seq

trait DocumentService {
  def generateInvoicePdf(order: Order): Future[URL]

  def generateReceiptPdf(order: Order): Future[URL]

  def generateInvoiceEmail(order: Order): Future[InputStream]

  def generateReceiptEmail(order: Order): Future[InputStream]

  def generateCancellationEmail(order: Order): Future[InputStream]

  def generateDeliveryNotePdf(order: Order): Future[URL]

  def generateDeliveryNoteEmail: Future[InputStream]

  def generateDeliveryManifestPdf(orders: Seq[Order]): Future[URL]

  def generateProductReportPdf(productReport: ProductReport): Future[URL]

  def generateProductReportEmail: Future[InputStream]
}


class DocumentServiceImpl(settings: DocumentSettings,
                          s3Service: S3Service,
                          mustacheFactory: MustacheFactory,
                          pdfService: PdfService,
                          fileNameService: FileNameService)(implicit executionContext: ExecutionContext) extends DocumentService {


  override def generateInvoicePdf(order: Order): Future[URL] =
    generatePdf(order, settings.invoicePdfTemplate, s"${order.invoiceDetails.invoiceNumber}-invoice.pdf")

  override def generateReceiptPdf(order: Order): Future[URL] =
    generatePdf(order, settings.receiptPdfTemplate, s"${order.invoiceDetails.invoiceNumber}-receipt.pdf")

  override def generateDeliveryNotePdf(order: Order): Future[URL] =
    generatePdf(order, settings.deliveryNotePdfTemplate, s"${order.invoiceDetails.invoiceNumber}-delivery_note.pdf")

  override def generateDeliveryManifestPdf(orders: Seq[Order]): Future[URL] =
    generatePdf(orders, settings.deliveryManifestPdfTemplate, "delivery_manifest.pdf", orientation = Landscape)

  override def generateProductReportPdf(productReport: ProductReport): Future[URL] =
    generatePdf(productReport, settings.productReportPdfTemplate, s"${productReport.date}-product_report.pdf")

  override def generateInvoiceEmail(order: Order): Future[InputStream] =
    generateHtml(order, settings.invoiceEmailTemplate)


  override def generateReceiptEmail(order: Order): Future[InputStream] =
    generateHtml(order, settings.receiptEmailTemplate)

  override def generateCancellationEmail(order: Order): Future[InputStream] =
    generateHtml(order, settings.cancellationEmailTemplate)

  override def generateDeliveryNoteEmail: Future[InputStream] =
    s3Service.getObject(settings.templatesBucket, settings.deliveryNoteEmailTemplate)

  override def generateProductReportEmail: Future[InputStream] =
    s3Service.getObject(settings.templatesBucket, settings.productReportEmailTemplate)


  private def generateHtml(data: Any, template: String): Future[InputStream] =
    for {
      templateInputStream <- s3Service.getObject(settings.templatesBucket, template)
      htmlInputStream = generateHtmlDocument(templateInputStream, mustacheFactory, template, data)
    } yield htmlInputStream

  private def generateHtmlDocument(templateInputStream: InputStream, mustacheFactory: MustacheFactory, name: String, data: Any): InputStream = {
    val pipedOutputStream = new PipedOutputStream()
    val pipedInputStream = new PipedInputStream(pipedOutputStream)
    val writer = new BufferedWriter(new OutputStreamWriter(pipedOutputStream))
    // Reader and writer of the piped stream should be on separate threads to avoid deadlocks
    Future {
      val mustache = mustacheFactory.compile(new InputStreamReader(templateInputStream), name)
      mustache.execute(writer, Map("data" -> data)).flush()
    }.onComplete { _ =>
      pipedOutputStream.close()
    }
    pipedInputStream
  }

  private def generatePdf(data: Any, template: String, outputFileName: String, orientation: Orientation = Portrait): Future[URL] =
    for {
      htmlInputStream <- generateHtml(data, template)
      pdfInputStream = pdfService.generate(htmlInputStream, orientation)
      outputPath = fileNameService.generatePath(outputFileName)
      outPutFileUrl <- s3Service.putObject(settings.documentsBucket, outputPath, pdfInputStream)
    } yield outPutFileUrl



}