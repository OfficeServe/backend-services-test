package com.officeserve.reportservice.settings

import java.net.URL

import com.officeserve.commons.dynamodb.DynamoDBSettings
import com.typesafe.config.Config

import scala.concurrent.duration._

class AppSettings(config: Config) {

  import AppSettings._

  val serverSettings = ServerSettings(config.getString("server.http.host"), config.getInt("server.http.port"))

  val reportSettings = ReportSettings(
    pendingMessageTTL = config.getScalaDuration("report.ttl.pendingMessage"),
    processedMessageTTL = config.getScalaFiniteDuration("report.ttl.processedMessage"),
    documentSettings = DocumentSettings(
      invoiceEmail = EmailSettings(
        from = config.getString("report.document.invoice.email.from"),
        bcc = config.getScalaSet("report.document.invoice.email.bcc"),
        subject = config.getString("report.document.invoice.email.subject")
      ),
      receiptEmail = EmailSettings(
        from = config.getString("report.document.receipt.email.from"),
        bcc = config.getScalaSet("report.document.receipt.email.bcc"),
        subject = config.getString("report.document.receipt.email.subject")
      ),
      cancellationEmail = EmailSettings(
        from = config.getString("report.document.cancellation.email.from"),
        bcc = config.getScalaSet("report.document.cancellation.email.bcc"),
        subject = config.getString("report.document.cancellation.email.subject")
      ),
      deliveryNoteEmail = EmailSettings(
        from = config.getString("report.document.deliveryNote.email.from"),
        to = config.getScalaSet("report.document.deliveryNote.email.to"),
        bcc = config.getScalaSet("report.document.deliveryNote.email.bcc"),
        subject = config.getString("report.document.deliveryNote.email.subject"),
        defaultBody = config.getString("report.document.deliveryNote.email.defaultBody")
      ),
      productReportEmail = EmailSettings(
        from = config.getString("report.document.productReport.email.from"),
        to = config.getScalaSet("report.document.productReport.email.to"),
        bcc = config.getScalaSet("report.document.productReport.email.bcc"),
        subject = config.getString("report.document.productReport.email.subject"),
        defaultBody = config.getString("report.document.productReport.email.defaultBody")
      )
    )
  )
  val redisSettings = RedisSettings(config.getString("aws.redis.host"), config.getInt("aws.redis.port"))
  val sqsSettings = SqsSettings(
    queueUrl = new URL(config.getString("aws.sqs.queueUrl")),
    maxMessages = config.getInt("aws.sqs.maxMessages"),
    fetchingInterval = config.getScalaFiniteDuration("aws.sqs.fetchingInterval"),
    writeTimeout = config.getScalaFiniteDuration("aws.sqs.sendMessageTimeout")
  )
  val snsSettings = SnsSettings(config.getString("aws.sns.endpoint"), config.getString("aws.sns.topicArn.email"))

  val dynamoDBSettings = DynamoDBSettings(config.getString("aws.dynamodb.endpoint"), config.getString("aws.dynamodb.tablePrefix"))

}

object AppSettings {

  implicit def enrichConfig(config: Config): RichConfig = new RichConfig(config)

  class RichConfig(config: Config) {

    def getScalaSet(path: String): Set[String] =
      config.getString(path).split(",").map(_.trim).toSet

    def getScalaDuration(path: String): Duration =
      Duration(config.getString(path))

    def getScalaFiniteDuration(path: String): FiniteDuration =
      FiniteDuration(getScalaDuration(path).toSeconds, SECONDS)

  }

}

case class ServerSettings(host: String, port: Int)

case class ReportSettings(pendingMessageTTL: Duration, processedMessageTTL: FiniteDuration, documentSettings: DocumentSettings)

case class RedisSettings(host: String, port: Int)

case class SqsSettings(queueUrl: URL, maxMessages: Int, fetchingInterval: FiniteDuration, writeTimeout: FiniteDuration)

case class SnsSettings(endpoint: String, emailTopicArn: String)

case class DocumentSettings(invoiceEmail: EmailSettings, receiptEmail: EmailSettings, cancellationEmail: EmailSettings, deliveryNoteEmail: EmailSettings, productReportEmail: EmailSettings)

case class EmailSettings(from: String, to: Set[String] = Set(), bcc: Set[String], subject: String, defaultBody: String = "")