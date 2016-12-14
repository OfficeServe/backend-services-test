package com.officeserve.documentservice.settings

import com.typesafe.config.Config

import scala.concurrent.duration._

class AppSettings(config: Config) {
  val serverSettings = ServerSettings(config.getString("server.http.host"), config.getInt("server.http.port"))
  val s3Settings: S3Settings = S3Settings(config.getString("aws.s3.endpoint"))
  val documentSettings = DocumentSettings(
    templatesBucket = config.getString("document.buckets.templates"),
    documentsBucket = config.getString("document.buckets.documents"),
    invoicePdfTemplate = config.getString("document.templates.invoice.pdf"),
    receiptPdfTemplate = config.getString("document.templates.receipt.pdf"),
    deliveryNotePdfTemplate = config.getString("document.templates.delivery-note.pdf"),
    deliveryManifestPdfTemplate = config.getString("document.templates.delivery-manifest.pdf"),
    productReportPdfTemplate = config.getString("document.templates.product-report.pdf"),
    invoiceEmailTemplate = config.getString("document.templates.invoice.email"),
    receiptEmailTemplate = config.getString("document.templates.receipt.email"),
    cancellationEmailTemplate = config.getString("document.templates.cancellation.email"),
    deliveryNoteEmailTemplate = config.getString("document.templates.delivery-note.email"),
    productReportEmailTemplate = config.getString("document.templates.product-report.email")
  )
  val retrySettings = RetrySettings(
    init = RetrySettingsEntry(
      delay = FiniteDuration(Duration(config.getString("retry.init.delay")).toSeconds, SECONDS),
      maxTimes = config.getInt("retry.init.maxTimes")
    )
  )
}

case class ServerSettings(host: String, port: Int)

case class S3Settings(endpoint: String)

case class DocumentSettings(templatesBucket: String,
                            documentsBucket: String,
                            invoicePdfTemplate: String,
                            receiptPdfTemplate: String,
                            deliveryNotePdfTemplate: String,
                            deliveryManifestPdfTemplate: String,
                            productReportPdfTemplate: String,
                            invoiceEmailTemplate: String,
                            receiptEmailTemplate: String,
                            cancellationEmailTemplate: String,
                            deliveryNoteEmailTemplate: String,
                            productReportEmailTemplate: String
                           )

case class RetrySettingsEntry(delay: FiniteDuration, maxTimes: Int)

case class RetrySettings(init: RetrySettingsEntry)