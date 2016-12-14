package com.officeserve.reportservice.services

import java.net.URL
import java.time.{LocalDate, ZoneId, ZonedDateTime}

import com.officeserve.basketservice.web._
import com.officeserve.documentservice.api.DocumentServiceApi
import com.officeserve.documentservice.models.{ProductReportItem, ProductReport => ProductReportRep, _}
import com.officeserve.reportservice.models.orderlog.OrderLog
import com.officeserve.reportservice.models.{Event, SendEmail, StoreOrders}
import com.officeserve.reportservice.models.productreport.{ProductId, ProductReportEntry, ProductReport => ProductReportModel}
import com.officeserve.reportservice.services.MessageConsumerService.ConsumerResult
import com.officeserve.reportservice.settings._
import com.officeserve.sendemail.model.{EmailMessage, Html}
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{AsyncFunSpec, Matchers}

import scala.collection.immutable.Seq
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future
import scala.util.{Failure, Success}

class MessageConsumerServiceTest extends AsyncFunSpec with Matchers with MockitoSugar with MessageConsumerServiceFixtures {
  describe("MessageConsumerService") {


    val settings = mock[AppSettings]

    val reportSettings = mock[ReportSettings]

    when(reportSettings.documentSettings) thenReturn documentSettings
    when(settings.reportSettings) thenReturn reportSettings
    when(settings.snsSettings) thenReturn snsSettings


    val order1 = mock[Order]
    val invoiceTo = mock[InvoiceTo]
    when(invoiceTo.email) thenReturn to
    when(order1.invoiceTo) thenReturn invoiceTo

    val order2 = mock[Order]
    val invoiceTo2 = mock[InvoiceTo]
    when(invoiceTo2.email) thenReturn to
    when(order1.invoiceTo) thenReturn invoiceTo


    describe("when sending OrderDocuments") {
      val event = Event(SendEmail, Set(EmailMessage(
        from = emailSettings.from,
        to = Set(order1.invoiceTo.email),
        cc = Set(),
        bcc = emailSettings.bcc,
        subject = emailSettings.subject,
        body = emailBody,
        attachments = Set(attachmentUrl1),
        format = Html
      )))

      describe("if the payment method is ON_ACCOUNT") {
        val orderMessageRep = mock[OrderMessageRep]
        val orderRep = mock[OrderRep]
        val paymentMethodRep = mock[PaymentMethodRep]
        when(paymentMethodRep.paymentType) thenReturn PaymentMethodType.onAccountType
        when(orderRep.paymentMethod) thenReturn Some(paymentMethodRep)
        when(orderMessageRep.order) thenReturn orderRep

        val messageTranslatorService = mock[MessageTranslatorService]
        when(messageTranslatorService.fromOrderMessageRepToOrder(ArgumentMatchers.eq(orderMessageRep))) thenReturn Success(order1)
        when(messageTranslatorService.fromEventToMessageBody(ArgumentMatchers.eq(event))) thenReturn messageBody

        val documentServiceApi = mock[DocumentServiceApi]
        when(documentServiceApi.generateInvoiceEmail(ArgumentMatchers.eq(order1))) thenReturn Future.successful(emailBody)
        when(documentServiceApi.generateInvoicePdf(ArgumentMatchers.eq(order1))) thenReturn Future.successful(attachmentUrl1)


        val snsService = mock[SnsService]
        when(snsService.publish(ArgumentMatchers.eq(snsSettings.emailTopicArn), ArgumentMatchers.eq(messageBody))) thenReturn Future.successful(messageId)

        val productReportService = mock[ProductReportService]

        val messageConsumerService = new MessageConsumerServiceImpl(settings, documentServiceApi, messageTranslatorService, snsService, productReportService)

        it("should generate an invoice") {
          messageConsumerService.sendOrderDocuments(Set(orderMessageRep)) map { result =>
            verify(messageTranslatorService, times(1)).fromOrderMessageRepToOrder(ArgumentMatchers.eq(orderMessageRep))
            verify(documentServiceApi, times(1)).generateInvoicePdf(ArgumentMatchers.eq(order1))
            verify(documentServiceApi, times(1)).generateInvoiceEmail(ArgumentMatchers.eq(order1))
            verify(messageTranslatorService, times(1)).fromEventToMessageBody(ArgumentMatchers.eq(event))
            verify(snsService, times(1)).publish(ArgumentMatchers.eq(snsSettings.emailTopicArn), ArgumentMatchers.eq(messageBody))

            result shouldBe ConsumerResult[OrderMessageRep](Set(orderMessageRep), Set())
          }
        }
      }
      describe("if the payment method is CREDIT_CARD") {
        val orderMessageRep = mock[OrderMessageRep]
        val orderRep = mock[OrderRep]
        val paymentMethodRep = mock[PaymentMethodRep]

        when(paymentMethodRep.paymentType) thenReturn PaymentMethodType.creditCardType
        when(orderRep.paymentMethod) thenReturn Some(paymentMethodRep)
        when(orderMessageRep.order) thenReturn orderRep

        val messageTranslatorService = mock[MessageTranslatorService]
        when(messageTranslatorService.fromOrderMessageRepToOrder(ArgumentMatchers.eq(orderMessageRep))) thenReturn Success(order1)
        when(messageTranslatorService.fromEventToMessageBody(ArgumentMatchers.eq(event))) thenReturn messageBody

        val documentServiceApi = mock[DocumentServiceApi]
        when(documentServiceApi.generateReceiptEmail(ArgumentMatchers.eq(order1))) thenReturn Future.successful(emailBody)
        when(documentServiceApi.generateReceiptPdf(ArgumentMatchers.eq(order1))) thenReturn Future.successful(attachmentUrl1)

        val snsService = mock[SnsService]
        when(snsService.publish(ArgumentMatchers.eq(snsSettings.emailTopicArn), ArgumentMatchers.eq(messageBody))) thenReturn Future.successful(messageId)

        val productReportService = mock[ProductReportService]

        val messageConsumerService = new MessageConsumerServiceImpl(settings, documentServiceApi, messageTranslatorService, snsService, productReportService)

        it("should generate a receipt") {
          messageConsumerService.sendOrderDocuments(Set(orderMessageRep)) map { result =>
            verify(messageTranslatorService, times(1)).fromOrderMessageRepToOrder(ArgumentMatchers.eq(orderMessageRep))
            verify(documentServiceApi, times(1)).generateReceiptPdf(ArgumentMatchers.eq(order1))
            verify(documentServiceApi, times(1)).generateReceiptEmail(ArgumentMatchers.eq(order1))
            verify(messageTranslatorService, times(1)).fromEventToMessageBody(ArgumentMatchers.eq(event))
            verify(snsService, times(1)).publish(ArgumentMatchers.eq(snsSettings.emailTopicArn), ArgumentMatchers.eq(messageBody))
            result shouldBe ConsumerResult[OrderMessageRep](Set(orderMessageRep), Set())
          }
        }
      }
    }
    describe("when sending Cancellations") {
      val event = Event(SendEmail, Set(EmailMessage(
        from = emailSettings.from,
        to = Set(order1.invoiceTo.email),
        cc = Set(),
        bcc = emailSettings.bcc,
        subject = emailSettings.subject,
        body = emailBody,
        attachments = Set(),
        format = Html
      )))

      val orderMessageRep = mock[OrderMessageRep]
      val orderRep = mock[OrderRep]
      when(orderMessageRep.order) thenReturn orderRep

      val messageTranslatorService = mock[MessageTranslatorService]
      when(messageTranslatorService.fromOrderMessageRepToOrder(ArgumentMatchers.eq(orderMessageRep))) thenReturn Success(order1)
      when(messageTranslatorService.fromEventToMessageBody(ArgumentMatchers.eq(event))) thenReturn messageBody

      val documentServiceApi = mock[DocumentServiceApi]
      when(documentServiceApi.generateCancellationEmail(ArgumentMatchers.eq(order1))) thenReturn Future.successful(emailBody)

      val snsService = mock[SnsService]
      when(snsService.publish(ArgumentMatchers.eq(snsSettings.emailTopicArn), ArgumentMatchers.eq(messageBody))) thenReturn Future.successful(messageId)

      val productReportService = mock[ProductReportService]

      val messageConsumerService = new MessageConsumerServiceImpl(settings, documentServiceApi, messageTranslatorService, snsService, productReportService)
      it("should send the cancellation email") {
        messageConsumerService.sendCancellation(Set(orderMessageRep)).map { result =>
          result shouldBe ConsumerResult[OrderMessageRep](Set(orderMessageRep), Set())
        }
      }
    }
    describe("when sending Delivery Notes") {


      val orderMessageRep1 = mock[OrderMessageRep]
      val orderRep1 = mock[OrderRep]
      when(orderMessageRep1.order) thenReturn orderRep1

      val orderMessageRep2 = mock[OrderMessageRep]
      val orderRep2 = mock[OrderRep]
      when(orderMessageRep2.order) thenReturn orderRep2

      describe("if there are no issues") {

        describe("and two orders") {
          val event = Event(SendEmail, Set(EmailMessage(
            from = emailSettings.from,
            to = emailSettings.to,
            cc = Set(),
            bcc = emailSettings.bcc,
            subject = emailSettings.subject,
            body = emailBody,
            attachments = Set(attachmentUrl1, attachmentUrl2, attachmentUrlDeliveryManifest),
            format = Html
          )))

          val messageTranslatorService = mock[MessageTranslatorService]
          when(messageTranslatorService.fromOrderMessageRepToOrder(ArgumentMatchers.eq(orderMessageRep1))) thenReturn Success(order1)
          when(messageTranslatorService.fromOrderMessageRepToOrder(ArgumentMatchers.eq(orderMessageRep2))) thenReturn Success(order2)
          when(messageTranslatorService.fromEventToMessageBody(ArgumentMatchers.eq(event))) thenReturn messageBody

          val documentServiceApi = mock[DocumentServiceApi]
          when(documentServiceApi.generateDeliveryNoteEmail()) thenReturn Future.successful(emailBody)
          when(documentServiceApi.generateDeliveryNotePdf(ArgumentMatchers.eq(order1))) thenReturn Future.successful(attachmentUrl1)
          when(documentServiceApi.generateDeliveryNotePdf(ArgumentMatchers.eq(order2))) thenReturn Future.successful(attachmentUrl2)
          when(documentServiceApi.generateDeliveryManifestPdf(ArgumentMatchers.eq(Seq(order1, order2)))) thenReturn Future.successful(attachmentUrlDeliveryManifest)

          val snsService = mock[SnsService]
          when(snsService.publish(ArgumentMatchers.eq(snsSettings.emailTopicArn), ArgumentMatchers.eq(messageBody))) thenReturn Future.successful(messageId)

          val productReportService = mock[ProductReportService]

          val messageConsumerService = new MessageConsumerServiceImpl(settings, documentServiceApi, messageTranslatorService, snsService, productReportService)
          it("should send the two delivery notes and one delivery manifest via email") {
            messageConsumerService.sendDeliveryNotes(Set(orderMessageRep1, orderMessageRep2)).map { result =>
              verify(documentServiceApi, times(1)).generateDeliveryNoteEmail()
              verify(documentServiceApi, times(1)).generateDeliveryNotePdf(ArgumentMatchers.eq(order1))
              verify(documentServiceApi, times(1)).generateDeliveryNotePdf(ArgumentMatchers.eq(order2))
              verify(documentServiceApi, times(1)).generateDeliveryManifestPdf(ArgumentMatchers.eq(Seq(order1, order2)))
              verify(snsService, times(1)).publish(ArgumentMatchers.eq(snsSettings.emailTopicArn), ArgumentMatchers.eq(messageBody))
              result shouldBe ConsumerResult[OrderMessageRep](Set(orderMessageRep1, orderMessageRep2), Set())
            }
          }
        }
        describe("and no orders") {
          val event = Event(SendEmail, Set(EmailMessage(
            from = emailSettings.from,
            to = emailSettings.to,
            cc = Set(),
            bcc = emailSettings.bcc,
            subject = emailSettings.subject,
            body = emailBody,
            attachments = Set(attachmentUrlDeliveryManifest),
            format = Html
          )))

          val messageTranslatorService = mock[MessageTranslatorService]
          when(messageTranslatorService.fromEventToMessageBody(ArgumentMatchers.eq(event))) thenReturn messageBody

          val documentServiceApi = mock[DocumentServiceApi]
          when(documentServiceApi.generateDeliveryNoteEmail()) thenReturn Future.successful(emailBody)
          when(documentServiceApi.generateDeliveryManifestPdf(ArgumentMatchers.eq(Seq.empty[Order]))) thenReturn Future.successful(attachmentUrlDeliveryManifest)

          val snsService = mock[SnsService]
          when(snsService.publish(ArgumentMatchers.eq(snsSettings.emailTopicArn), ArgumentMatchers.eq(messageBody))) thenReturn Future.successful(messageId)

          val productReportService = mock[ProductReportService]

          val messageConsumerService = new MessageConsumerServiceImpl(settings, documentServiceApi, messageTranslatorService, snsService, productReportService)
          it("should send an email with no delivery notes and no manifest") {
            messageConsumerService.sendDeliveryNotes(Set()).map { result =>
              verify(documentServiceApi, times(1)).generateDeliveryNoteEmail()
              verify(documentServiceApi, never()).generateDeliveryNotePdf(ArgumentMatchers.any[Order])
              verify(documentServiceApi, times(1)).generateDeliveryManifestPdf(ArgumentMatchers.eq(Seq()))

              result shouldBe ConsumerResult[OrderMessageRep](Set(), Set())
            }
          }
        }
      }
      describe("if there are some issues") {
        describe("if we fail to convert one order from OrderMessageRep to Order") {
          val event = Event(SendEmail, Set(EmailMessage(
            from = emailSettings.from,
            to = emailSettings.to,
            cc = Set(),
            bcc = emailSettings.bcc,
            subject = emailSettings.subject,
            body = emailBody,
            attachments = Set(attachmentUrl2, attachmentUrlDeliveryManifest),
            format = Html
          )))

          val exception = new Exception("test-exception")

          val messageTranslatorService = mock[MessageTranslatorService]
          when(messageTranslatorService.fromOrderMessageRepToOrder(ArgumentMatchers.eq(orderMessageRep1))) thenReturn Failure(exception)
          when(messageTranslatorService.fromOrderMessageRepToOrder(ArgumentMatchers.eq(orderMessageRep2))) thenReturn Success(order2)
          when(messageTranslatorService.fromEventToMessageBody(ArgumentMatchers.eq(event))) thenReturn messageBody

          val documentServiceApi = mock[DocumentServiceApi]
          when(documentServiceApi.generateDeliveryNoteEmail()) thenReturn Future.successful(emailBody)
          when(documentServiceApi.generateDeliveryNotePdf(ArgumentMatchers.eq(order2))) thenReturn Future.successful(attachmentUrl2)
          when(documentServiceApi.generateDeliveryManifestPdf(ArgumentMatchers.eq(Seq(order2)))) thenReturn Future.successful(attachmentUrlDeliveryManifest)

          val snsService = mock[SnsService]
          when(snsService.publish(ArgumentMatchers.eq(snsSettings.emailTopicArn), ArgumentMatchers.eq(messageBody))) thenReturn Future.successful(messageId)

          val productReportService = mock[ProductReportService]

          val messageConsumerService = new MessageConsumerServiceImpl(settings, documentServiceApi, messageTranslatorService, snsService, productReportService)
          it("should send an email with the remaining delivery notes and the manifest") {
            messageConsumerService.sendDeliveryNotes(Set(orderMessageRep1, orderMessageRep2)).map { result =>
              verify(documentServiceApi, times(1)).generateDeliveryNoteEmail()
              verify(documentServiceApi, times(1)).generateDeliveryNotePdf(ArgumentMatchers.eq(order2))
              verify(documentServiceApi, times(1)).generateDeliveryManifestPdf(ArgumentMatchers.eq(Seq(order2)))
              verify(snsService, times(1)).publish(ArgumentMatchers.eq(snsSettings.emailTopicArn), ArgumentMatchers.eq(messageBody))
              result shouldBe ConsumerResult[OrderMessageRep](Set(orderMessageRep2), Set((orderMessageRep1, exception)))
            }
          }
        }
        describe("if we fail to generate one delivery note") {
          val event = Event(SendEmail, Set(EmailMessage(
            from = emailSettings.from,
            to = emailSettings.to,
            cc = Set(),
            bcc = emailSettings.bcc,
            subject = emailSettings.subject,
            body = emailBody,
            attachments = Set(attachmentUrl2, attachmentUrlDeliveryManifest),
            format = Html
          )))

          val exception = new Exception("test-exception")

          val messageTranslatorService = mock[MessageTranslatorService]
          when(messageTranslatorService.fromOrderMessageRepToOrder(ArgumentMatchers.eq(orderMessageRep1))) thenReturn Success(order1)
          when(messageTranslatorService.fromOrderMessageRepToOrder(ArgumentMatchers.eq(orderMessageRep2))) thenReturn Success(order2)
          when(messageTranslatorService.fromEventToMessageBody(ArgumentMatchers.eq(event))) thenReturn messageBody

          val documentServiceApi = mock[DocumentServiceApi]
          when(documentServiceApi.generateDeliveryNoteEmail()) thenReturn Future.successful(emailBody)
          when(documentServiceApi.generateDeliveryNotePdf(ArgumentMatchers.eq(order1))) thenReturn Future.failed(exception)
          when(documentServiceApi.generateDeliveryNotePdf(ArgumentMatchers.eq(order2))) thenReturn Future.successful(attachmentUrl2)
          when(documentServiceApi.generateDeliveryManifestPdf(ArgumentMatchers.eq(Seq(order2)))) thenReturn Future.successful(attachmentUrlDeliveryManifest)

          val snsService = mock[SnsService]
          when(snsService.publish(ArgumentMatchers.eq(snsSettings.emailTopicArn), ArgumentMatchers.eq(messageBody))) thenReturn Future.successful(messageId)

          val productReportService = mock[ProductReportService]

          val messageConsumerService = new MessageConsumerServiceImpl(settings, documentServiceApi, messageTranslatorService, snsService, productReportService)
          it("should send an email with the remaining delivery notes and the manifest") {
            messageConsumerService.sendDeliveryNotes(Set(orderMessageRep1, orderMessageRep2)).map { result =>
              verify(documentServiceApi, times(1)).generateDeliveryNoteEmail()
              verify(documentServiceApi, times(1)).generateDeliveryNotePdf(ArgumentMatchers.eq(order1))
              verify(documentServiceApi, times(1)).generateDeliveryNotePdf(ArgumentMatchers.eq(order2))
              verify(documentServiceApi, times(1)).generateDeliveryManifestPdf(ArgumentMatchers.eq(Seq(order2)))
              verify(snsService, times(1)).publish(ArgumentMatchers.eq(snsSettings.emailTopicArn), ArgumentMatchers.eq(messageBody))
              result shouldBe ConsumerResult[OrderMessageRep](Set(orderMessageRep2), Set((orderMessageRep1, exception)))
            }
          }
        }
        describe("if we fail to generate the manifest") {
          val event = Event(SendEmail, Set(EmailMessage(
            from = emailSettings.from,
            to = emailSettings.to,
            cc = Set(),
            bcc = emailSettings.bcc,
            subject = emailSettings.subject,
            body = emailBody,
            attachments = Set(attachmentUrl1, attachmentUrl2),
            format = Html
          )))

          val exception = new Exception("test-exception")

          val messageTranslatorService = mock[MessageTranslatorService]
          when(messageTranslatorService.fromOrderMessageRepToOrder(ArgumentMatchers.eq(orderMessageRep1))) thenReturn Success(order1)
          when(messageTranslatorService.fromOrderMessageRepToOrder(ArgumentMatchers.eq(orderMessageRep2))) thenReturn Success(order2)
          when(messageTranslatorService.fromEventToMessageBody(ArgumentMatchers.eq(event))) thenReturn messageBody

          val documentServiceApi = mock[DocumentServiceApi]
          when(documentServiceApi.generateDeliveryNoteEmail()) thenReturn Future.successful(emailBody)
          when(documentServiceApi.generateDeliveryNotePdf(ArgumentMatchers.eq(order1))) thenReturn Future.successful(attachmentUrl1)
          when(documentServiceApi.generateDeliveryNotePdf(ArgumentMatchers.eq(order2))) thenReturn Future.successful(attachmentUrl2)
          when(documentServiceApi.generateDeliveryManifestPdf(ArgumentMatchers.eq(Seq(order1, order2)))) thenReturn Future.failed(exception)

          val snsService = mock[SnsService]
          when(snsService.publish(ArgumentMatchers.eq(snsSettings.emailTopicArn), ArgumentMatchers.eq(messageBody))) thenReturn Future.successful(messageId)

          val productReportService = mock[ProductReportService]

          val messageConsumerService = new MessageConsumerServiceImpl(settings, documentServiceApi, messageTranslatorService, snsService, productReportService)
          it("should send an email with the delivery notes") {
            messageConsumerService.sendDeliveryNotes(Set(orderMessageRep1, orderMessageRep2)).map { result =>
              verify(documentServiceApi, times(1)).generateDeliveryNoteEmail()
              verify(documentServiceApi, times(1)).generateDeliveryNotePdf(ArgumentMatchers.eq(order1))
              verify(documentServiceApi, times(1)).generateDeliveryNotePdf(ArgumentMatchers.eq(order2))
              verify(documentServiceApi, times(1)).generateDeliveryManifestPdf(ArgumentMatchers.eq(Seq(order1, order2)))
              verify(snsService, times(1)).publish(ArgumentMatchers.eq(snsSettings.emailTopicArn), ArgumentMatchers.eq(messageBody))
              result shouldBe ConsumerResult[OrderMessageRep](Set(orderMessageRep1, orderMessageRep2), Set())
            }
          }
        }
        describe("if we fail to generate the email body") {
          val event = Event(SendEmail, Set(EmailMessage(
            from = emailSettings.from,
            to = emailSettings.to,
            cc = Set(),
            bcc = emailSettings.bcc,
            subject = emailSettings.subject,
            body = emailSettings.defaultBody,
            attachments = Set(attachmentUrl1, attachmentUrl2, attachmentUrlDeliveryManifest),
            format = Html
          )))

          val exception = new Exception("test-exception")

          val messageTranslatorService = mock[MessageTranslatorService]
          when(messageTranslatorService.fromOrderMessageRepToOrder(ArgumentMatchers.eq(orderMessageRep1))) thenReturn Success(order1)
          when(messageTranslatorService.fromOrderMessageRepToOrder(ArgumentMatchers.eq(orderMessageRep2))) thenReturn Success(order2)
          when(messageTranslatorService.fromEventToMessageBody(ArgumentMatchers.eq(event))) thenReturn messageBody

          val documentServiceApi = mock[DocumentServiceApi]
          when(documentServiceApi.generateDeliveryNoteEmail()) thenReturn Future.failed(exception)
          when(documentServiceApi.generateDeliveryNotePdf(ArgumentMatchers.eq(order1))) thenReturn Future.successful(attachmentUrl1)
          when(documentServiceApi.generateDeliveryNotePdf(ArgumentMatchers.eq(order2))) thenReturn Future.successful(attachmentUrl2)
          when(documentServiceApi.generateDeliveryManifestPdf(ArgumentMatchers.eq(Seq(order1, order2)))) thenReturn Future.successful(attachmentUrlDeliveryManifest)

          val snsService = mock[SnsService]
          when(snsService.publish(ArgumentMatchers.eq(snsSettings.emailTopicArn), ArgumentMatchers.eq(messageBody))) thenReturn Future.successful(messageId)

          val productReportService = mock[ProductReportService]

          val messageConsumerService = new MessageConsumerServiceImpl(settings, documentServiceApi, messageTranslatorService, snsService, productReportService)
          it("should send the mail anyway using the default email body") {
            messageConsumerService.sendDeliveryNotes(Set(orderMessageRep1, orderMessageRep2)).map { result =>
              verify(documentServiceApi, times(1)).generateDeliveryNoteEmail()
              verify(documentServiceApi, times(1)).generateDeliveryNotePdf(ArgumentMatchers.eq(order1))
              verify(documentServiceApi, times(1)).generateDeliveryNotePdf(ArgumentMatchers.eq(order2))
              verify(documentServiceApi, times(1)).generateDeliveryManifestPdf(ArgumentMatchers.eq(Seq(order1, order2)))
              verify(snsService, times(1)).publish(ArgumentMatchers.eq(snsSettings.emailTopicArn), ArgumentMatchers.eq(messageBody))
              result shouldBe ConsumerResult[OrderMessageRep](Set(orderMessageRep1, orderMessageRep2), Set())
            }
          }
        }
        describe("if we fail to send the email") {
          val event = Event(SendEmail, Set(EmailMessage(
            from = emailSettings.from,
            to = emailSettings.to,
            cc = Set(),
            bcc = emailSettings.bcc,
            subject = emailSettings.subject,
            body = emailBody,
            attachments = Set(attachmentUrl1, attachmentUrl2, attachmentUrlDeliveryManifest),
            format = Html
          )))

          val exception = new Exception("test-exception")

          val messageTranslatorService = mock[MessageTranslatorService]
          when(messageTranslatorService.fromOrderMessageRepToOrder(ArgumentMatchers.eq(orderMessageRep1))) thenReturn Success(order1)
          when(messageTranslatorService.fromOrderMessageRepToOrder(ArgumentMatchers.eq(orderMessageRep2))) thenReturn Success(order2)
          when(messageTranslatorService.fromEventToMessageBody(ArgumentMatchers.eq(event))) thenReturn messageBody

          val documentServiceApi = mock[DocumentServiceApi]
          when(documentServiceApi.generateDeliveryNoteEmail()) thenReturn Future.successful(emailBody)
          when(documentServiceApi.generateDeliveryNotePdf(ArgumentMatchers.eq(order1))) thenReturn Future.successful(attachmentUrl1)
          when(documentServiceApi.generateDeliveryNotePdf(ArgumentMatchers.eq(order2))) thenReturn Future.successful(attachmentUrl2)
          when(documentServiceApi.generateDeliveryManifestPdf(ArgumentMatchers.eq(Seq(order1, order2)))) thenReturn Future.successful(attachmentUrlDeliveryManifest)

          val snsService = mock[SnsService]
          when(snsService.publish(ArgumentMatchers.eq(snsSettings.emailTopicArn), ArgumentMatchers.eq(messageBody))) thenReturn Future.failed(exception)

          val productReportService = mock[ProductReportService]

          val messageConsumerService = new MessageConsumerServiceImpl(settings, documentServiceApi, messageTranslatorService, snsService, productReportService)
          it("should mark all orders as failed") {
            messageConsumerService.sendDeliveryNotes(Set(orderMessageRep1, orderMessageRep2)).map { result =>
              verify(documentServiceApi, times(1)).generateDeliveryNoteEmail()
              verify(documentServiceApi, times(1)).generateDeliveryNotePdf(ArgumentMatchers.eq(order1))
              verify(documentServiceApi, times(1)).generateDeliveryNotePdf(ArgumentMatchers.eq(order2))
              verify(documentServiceApi, times(1)).generateDeliveryManifestPdf(ArgumentMatchers.eq(Seq(order1, order2)))
              verify(snsService, times(1)).publish(ArgumentMatchers.eq(snsSettings.emailTopicArn), ArgumentMatchers.eq(messageBody))
              result shouldBe ConsumerResult[OrderMessageRep](Set(), Set((orderMessageRep1, exception), (orderMessageRep2, exception)))
            }
          }
        }
      }

    }
    describe("when storing Orders") {
      describe("if there are no issues") {
        describe("and two orders") {

          val now = ZonedDateTime.of(2016, 11, 24, 1, 1, 1, 1, ZoneId.systemDefault())

          val orderMessageRep1 = mock[OrderMessageRep]
          val orderRep1 = mock[OrderRep]
          when(orderRep1.deliveryDate) thenReturn Some(now)
          when(orderRep1.orderStatus) thenReturn OrderStatusRep("Pending")
          when(orderRep1.id) thenReturn "id1"
          when(orderRep1.cutOffTime) thenReturn Some(now)
          when(orderMessageRep1.order) thenReturn orderRep1

          val orderMessageRep2 = mock[OrderMessageRep]
          val orderRep2 = mock[OrderRep]
          when(orderRep2.deliveryDate) thenReturn Some(now)
          when(orderRep2.orderStatus) thenReturn OrderStatusRep("Pending")
          when(orderRep2.id) thenReturn "id2"
          when(orderRep2.cutOffTime) thenReturn Some(now)
          when(orderMessageRep2.order) thenReturn orderRep2

          val event = Event(StoreOrders, Set(orderMessageRep1, orderMessageRep2))

          val documentServiceApi = mock[DocumentServiceApi]

          val messageTranslatorService = mock[MessageTranslatorService]
          when(messageTranslatorService.fromOrderMessageRepToOrder(ArgumentMatchers.eq(orderMessageRep1))) thenReturn Success(order1)
          when(messageTranslatorService.fromOrderMessageRepToOrder(ArgumentMatchers.eq(orderMessageRep2))) thenReturn Success(order2)
          when(messageTranslatorService.fromEventToMessageBody(ArgumentMatchers.eq(event))) thenReturn messageBody

          val snsService = mock[SnsService]

          val productReportService = mock[ProductReportService]
          when(productReportService.storeOrderLog(ArgumentMatchers.any[OrderLog])) thenReturn Future.successful({})


          val messageConsumerService = new MessageConsumerServiceImpl(settings, documentServiceApi, messageTranslatorService, snsService, productReportService)

          it("should store them in dynamodb") {
            messageConsumerService.storeOrders(Set(orderMessageRep1, orderMessageRep2)).map { result =>
              verify(productReportService, times(1)).storeOrderLog(OrderLog(now.toLocalDate, "Pending_id1", orderMessageRep1))
              verify(productReportService, times(1)).storeOrderLog(OrderLog(now.toLocalDate, "Pending_id2", orderMessageRep2))
              result shouldBe ConsumerResult[OrderMessageRep](Set(orderMessageRep1, orderMessageRep2), Set())
            }

          }
        }
      }
    }
    describe("when sending a product report") {

      val event = Event(SendEmail, Set(EmailMessage(
        from = emailSettings.from,
        to = emailSettings.to,
        cc = Set(),
        bcc = emailSettings.bcc,
        subject = emailSettings.subject,
        body = emailBody,
        attachments = Set(attachmentUrl1),
        format = Html
      )))
      describe("if there are no issues") {

        val productReportService = mock[ProductReportService]
        val productReport = ProductReportModel(LocalDate.now(), ZonedDateTime.now(), Map[ProductId, ProductReportEntry]())
        when(productReportService.generate) thenReturn Future.successful(productReport)

        val messageTranslatorService = mock[MessageTranslatorService]
        val productReportRep = ProductReportRep("2016-12-01", List(
          ProductReportItem("productCode1", "productName1", "previous1", "difference1", "total1", "deliveryDate1"),
          ProductReportItem("productCode2", "productName2", "previous2", "difference2", "total2", "deliveryDate2")
        ))
        when(messageTranslatorService.fromProductReportToProductReportRep(productReport)) thenReturn productReportRep
        when(messageTranslatorService.fromEventToMessageBody(ArgumentMatchers.eq(event))) thenReturn messageBody

        val documentServiceApi = mock[DocumentServiceApi]
        when(documentServiceApi.generateProductReportEmail) thenReturn Future.successful(emailBody)
        when(documentServiceApi.generateProductReportPdf(productReportRep)) thenReturn Future.successful(attachmentUrl1)


        val snsService = mock[SnsService]
        when(snsService.publish(ArgumentMatchers.eq(snsSettings.emailTopicArn), ArgumentMatchers.eq(messageBody))) thenReturn Future.successful(messageId)

        val messageConsumerService = new MessageConsumerServiceImpl(settings, documentServiceApi, messageTranslatorService, snsService, productReportService)
        it("should generate the current report and send it via email (SNS)") {
          messageConsumerService.sendProductReport.map { result =>
            verify(snsService, times(1)).publish(ArgumentMatchers.eq(snsSettings.emailTopicArn), ArgumentMatchers.eq(messageBody))
            result shouldBe ConsumerResult[OrderMessageRep](Set.empty, Set.empty)
          }
        }
      }
      describe("if there are some issues") {
        describe("if we fail to generate the email body") {
          val eventWithDefaultEmailBody = event.copy(entities = event.entities.map(_.copy(body = emailSettings.defaultBody)))

          val productReportService = mock[ProductReportService]
          val productReport = ProductReportModel(LocalDate.now(), ZonedDateTime.now(), Map[ProductId, ProductReportEntry]())
          when(productReportService.generate) thenReturn Future.successful(productReport)

          val messageTranslatorService = mock[MessageTranslatorService]
          val productReportRep = ProductReportRep("2016-12-01", List(
            ProductReportItem("productCode1", "productName1", "previous1", "difference1", "total1", "deliveryDate1"),
            ProductReportItem("productCode2", "productName2", "previous2", "difference2", "total2", "deliveryDate2")
          ))
          when(messageTranslatorService.fromProductReportToProductReportRep(productReport)) thenReturn productReportRep
          when(messageTranslatorService.fromEventToMessageBody(ArgumentMatchers.eq(eventWithDefaultEmailBody))) thenReturn messageBody

          val documentServiceApi = mock[DocumentServiceApi]
          when(documentServiceApi.generateProductReportEmail) thenReturn Future.failed(new Exception("test-failure"))
          when(documentServiceApi.generateProductReportPdf(productReportRep)) thenReturn Future.successful(attachmentUrl1)


          val snsService = mock[SnsService]
          when(snsService.publish(ArgumentMatchers.eq(snsSettings.emailTopicArn), ArgumentMatchers.eq(messageBody))) thenReturn Future.successful(messageId)

          val messageConsumerService = new MessageConsumerServiceImpl(settings, documentServiceApi, messageTranslatorService, snsService, productReportService)

          it("should send the mail anyway using the default email body") {
            messageConsumerService.sendProductReport.map { result =>
              verify(snsService, times(1)).publish(ArgumentMatchers.eq(snsSettings.emailTopicArn), ArgumentMatchers.eq(messageBody))
              result shouldBe ConsumerResult[OrderMessageRep](Set.empty, Set.empty)
            }
          }
        }
        describe("if we fail to generate the pdf") {
          val productReportService = mock[ProductReportService]
          val productReport = ProductReportModel(LocalDate.now(), ZonedDateTime.now(), Map[ProductId, ProductReportEntry]())
          when(productReportService.generate) thenReturn Future.successful(productReport)

          val messageTranslatorService = mock[MessageTranslatorService]
          val productReportRep = ProductReportRep("2016-12-01", List(
            ProductReportItem("productCode1", "productName1", "previous1", "difference1", "total1", "deliveryDate1"),
            ProductReportItem("productCode2", "productName2", "previous2", "difference2", "total2", "deliveryDate2")
          ))
          when(messageTranslatorService.fromProductReportToProductReportRep(productReport)) thenReturn productReportRep
          when(messageTranslatorService.fromEventToMessageBody(ArgumentMatchers.eq(event))) thenReturn messageBody

          val documentServiceApi = mock[DocumentServiceApi]
          when(documentServiceApi.generateProductReportEmail) thenReturn Future.successful(emailBody)
          when(documentServiceApi.generateProductReportPdf(productReportRep)) thenReturn Future.failed(new Exception("test-exception"))


          val snsService = mock[SnsService]
          when(snsService.publish(ArgumentMatchers.eq(snsSettings.emailTopicArn), ArgumentMatchers.eq(messageBody))) thenReturn Future.successful(messageId)

          val messageConsumerService = new MessageConsumerServiceImpl(settings, documentServiceApi, messageTranslatorService, snsService, productReportService)

          it("should return a failure") {
            recoverToSucceededIf[Exception] {
              messageConsumerService.sendProductReport
            }
          }
        }
      }
    }
  }

}

trait MessageConsumerServiceFixtures {
  val to = "to@officeserve.com"
  val emailBody = "emailBody"
  val attachmentUrl1 = new URL("http://localhost")
  val attachmentUrl2 = new URL("http://localhost2")
  val attachmentUrlDeliveryManifest = new URL("http://localhost-manifest")
  val emailSettings = EmailSettings(from = "from@officeserve.com", to = Set("to@officeserve.com"), bcc = Set("bcc@officeserve.com"), subject = "subject", defaultBody = "defaultBody")
  val documentSettings = DocumentSettings(invoiceEmail = emailSettings, receiptEmail = emailSettings, cancellationEmail = emailSettings, deliveryNoteEmail = emailSettings, productReportEmail = emailSettings)
  val snsSettings = SnsSettings("http://endpoint", "emailTopic")
  val messageBody = "messageBody"
  val messageId = "messageId"
}
