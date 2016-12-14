package com.officeserve.reportservice.services

import java.net.URL

import com.officeserve.basketservice.web.{OrderMessageRep, PaymentMethodType}
import com.officeserve.documentservice.api.DocumentServiceApi
import com.officeserve.documentservice.models.Order
import com.officeserve.reportservice.models.orderlog.OrderLog
import com.officeserve.reportservice.models.{Event, SendEmail}
import com.officeserve.reportservice.services.MessageConsumerService.ConsumerResult
import com.officeserve.reportservice.services.MessageConsumerService.Exceptions.{ConversionException, MissingCutOffTimeException, MissingPaymentMethodException, UnexpectedPaymentTypeException}
import com.officeserve.reportservice.services.SnsService.MessageId
import com.officeserve.reportservice.settings.{AppSettings, EmailSettings}
import com.officeserve.sendemail.model.{EmailMessage, Html}
import com.typesafe.scalalogging.LazyLogging

import scala.collection.immutable.Seq
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Right, Success}

trait MessageConsumerService {
  def sendOrderDocuments(orders: Set[OrderMessageRep]): Future[ConsumerResult[OrderMessageRep]]

  def sendCancellation(orders: Set[OrderMessageRep]): Future[ConsumerResult[OrderMessageRep]]

  def sendDeliveryNotes(orders: Set[OrderMessageRep]): Future[ConsumerResult[OrderMessageRep]]

  def storeOrders(orders: Set[OrderMessageRep]): Future[ConsumerResult[OrderMessageRep]]

  def sendProductReport: Future[ConsumerResult[OrderMessageRep]]

}

object MessageConsumerService {

  case class ConsumerResult[T](succeeded: Set[T], failed: Set[(T, Throwable)])

  object Exceptions {

    sealed trait MessageConsumerException extends Exception

    case class MissingPaymentMethodException(order: OrderMessageRep) extends Exception(s"Missing payment method in order \n$order") with MessageConsumerException

    case class ConversionException(order: OrderMessageRep, cause: Throwable) extends Exception(s"Error during order conversion \n$order", cause) with MessageConsumerException

    case class UnexpectedPaymentTypeException(order: OrderMessageRep) extends Exception(s"Unexpected Payment Type \n$order") with MessageConsumerException

    case class MissingCutOffTimeException(order: OrderMessageRep) extends Exception(s"Missing cut-off time in order \n$order") with MessageConsumerException

  }

}

class MessageConsumerServiceImpl(settings: AppSettings,
                                 documentServiceApi: DocumentServiceApi,
                                 messageTranslatorService: MessageTranslatorService,
                                 snsService: SnsService,
                                 productReportService: ProductReportService
                                )(implicit ec: ExecutionContext) extends MessageConsumerService with LazyLogging {


  override def sendOrderDocuments(orders: Set[OrderMessageRep]): Future[ConsumerResult[OrderMessageRep]] =
    process(orders) { curr =>
      curr.order.paymentMethod.fold[Future[Either[(OrderMessageRep, Throwable), OrderMessageRep]]] {
        Future.successful(Left((curr, MissingPaymentMethodException(curr))))
      } {
        paymentMethod =>
          (paymentMethod.paymentType, messageTranslatorService.fromOrderMessageRepToOrder(curr)) match {
            case (PaymentMethodType.onAccountType, Success(invoice)) =>

              val attachmentUrl = documentServiceApi.generateInvoicePdf(invoice)
              val emailBody = documentServiceApi.generateInvoiceEmail(invoice)
              val emailSettings = settings.reportSettings.documentSettings.invoiceEmail

              sendEmailWithResponseHandling(curr, invoice, Some(attachmentUrl), emailBody, emailSettings)

            case (PaymentMethodType.creditCardType, Success(invoice)) =>
              val attachmentUrl = documentServiceApi.generateReceiptPdf(invoice)
              val emailBody = documentServiceApi.generateReceiptEmail(invoice)
              val emailSettings = settings.reportSettings.documentSettings.receiptEmail

              sendEmailWithResponseHandling(curr, invoice, Some(attachmentUrl), emailBody, emailSettings)

            case (_, Failure(e)) =>
              Future.successful(Left(curr, ConversionException(curr, e)))
            case (_, Success(invoice)) =>
              Future.successful(Left(curr, UnexpectedPaymentTypeException(curr)))
          }
      }
    }


  override def sendCancellation(orders: Set[OrderMessageRep]): Future[ConsumerResult[OrderMessageRep]] =
    process(orders) { curr =>
      messageTranslatorService.fromOrderMessageRepToOrder(curr) match {

        case Success(order) =>
          val emailBody = documentServiceApi.generateCancellationEmail(order)
          val emailSettings = settings.reportSettings.documentSettings.cancellationEmail

          sendEmailWithResponseHandling(curr, order, attachmentUrl = None, emailBody, emailSettings)
        case Failure(e) =>
          Future.successful(Left(curr, e))
      }
    }


  override def sendDeliveryNotes(orders: Set[OrderMessageRep]): Future[ConsumerResult[OrderMessageRep]] = {

    type FailedOrder = (OrderMessageRep, Throwable)
    type SuccessFulOrder = (OrderMessageRep, Order, URL)

    val failuresOrUrls: Future[Set[Either[FailedOrder, SuccessFulOrder]]] = Future.traverse(orders) { curr =>
      messageTranslatorService.fromOrderMessageRepToOrder(curr) match {
        case Success(order) =>
          documentServiceApi.generateDeliveryNotePdf(order)
            .map(url => Right((curr, order, url)))
            .recoverWith {
              case e => Future.successful(Left(curr, e))
            }

        case Failure(e) =>
          Future.successful(Left(curr, e))
      }
    }

    val emailSettings = settings.reportSettings.documentSettings.deliveryNoteEmail

    val emailBodyFuture = documentServiceApi.generateDeliveryNoteEmail()
      .recover {
        case e =>
          logger.error("Error during generation of delivery note email body. Using default email body", e)
          emailSettings.defaultBody
      }

    for {
      result <- failuresOrUrls
      emailBody <- emailBodyFuture

      failed = result.collect {
        case Left(failure) => failure
      }

      (succeeded, orders, urls) = result
        .collect {
          case Right((orderMessageRep, order, url)) => (orderMessageRep, order, url)
        }.unzip3

      attachmentUrls <- documentServiceApi.generateDeliveryManifestPdf(orders.to[Seq])
        .map { deliveryManifestUrl =>
          urls + deliveryManifestUrl
        }.recover {
        case e =>
          logger.error(s"Error during delivery manifest generation. Sending the delivery notes anyway", e)
          urls
      }

      result <- sendEmail(emailSettings.to, attachmentUrls, emailBody, emailSettings)
        .map { messageId =>
          logger.debug(s"Email message published to SNS with messageId [$messageId]")
          ConsumerResult(succeeded, failed)
        }
        .recover {
          case e =>
            logger.error("An error occurred whilst sending the email. Marking all messages as failed", e)
            ConsumerResult(Set(), failed ++ succeeded.map(orderMessageRep => (orderMessageRep, e)))
        }

    } yield result

  }

  override def storeOrders(orders: Set[OrderMessageRep]): Future[ConsumerResult[OrderMessageRep]] =
    process(orders) { curr =>
      curr.order.cutOffTime.fold[Future[Either[(OrderMessageRep, Throwable), OrderMessageRep]]] {
        Future.successful(Left((curr, MissingCutOffTimeException(curr))))
      } { cutoffTime =>
        val day = cutoffTime.toLocalDate
        val eventId = s"${curr.order.orderStatus.status}_${curr.order.id}"

        productReportService.storeOrderLog(OrderLog(day, eventId, curr))
          .map { _ => Right(curr) }
          .recover {
            case e => Left((curr, e))
          }
      }
    }

  override def sendProductReport: Future[ConsumerResult[OrderMessageRep]] = {
    val emailSettings = settings.reportSettings.documentSettings.productReportEmail

    val productReportEmailFuture = documentServiceApi.generateProductReportEmail()
      .recover {
        case e =>
          logger.error("Error during email body generation. Using the default template", e)
          emailSettings.defaultBody
      }

    for {
      productReport <- productReportService.generate
      documentApiProductReport = messageTranslatorService.fromProductReportToProductReportRep(productReport)
      emailBody <- productReportEmailFuture
      attachmentUrl <- documentServiceApi.generateProductReportPdf(documentApiProductReport)
      _ <- sendEmail(emailSettings.to, Set(attachmentUrl), emailBody, emailSettings)
    } yield {
      ConsumerResult[OrderMessageRep](
        succeeded = Set.empty,
        failed = Set.empty
      )
    }

  }

  private[this] def process[T](entities: Set[T])(body: (T) => Future[Either[(T, Throwable), T]]): Future[ConsumerResult[T]] =
    Future.traverse(entities) {
      curr =>
        body(curr)
    }.map {
      result =>
        result.foldLeft(ConsumerResult[T](Set(), Set())) {
          (acc, curr) =>
            curr match {
              case Left(failure) => acc.copy(failed = acc.failed + failure)
              case Right(success) => acc.copy(succeeded = acc.succeeded + success)
            }
        }
    }

  private[this] def sendEmail(emailTo: Set[String], attachmentUrls: Set[URL], emailBody: String, emailSettings: EmailSettings): Future[MessageId] = {
    val event = Event(SendEmail, Set(EmailMessage(
      from = emailSettings.from,
      to = emailTo,
      cc = Set(),
      bcc = emailSettings.bcc,
      subject = emailSettings.subject,
      body = emailBody,
      attachments = attachmentUrls,
      format = Html
    )))

    val messageBody = messageTranslatorService.fromEventToMessageBody(event)

    snsService.publish(settings.snsSettings.emailTopicArn, messageBody)
  }

  private[this] def sendEmailWithResponseHandling(curr: OrderMessageRep, order: Order, attachmentUrl: Option[Future[URL]] = None, emailBody: Future[String], emailSettings: EmailSettings): Future[Either[(OrderMessageRep, Throwable), OrderMessageRep]] = {
    def optionFutureToFutureSet[T](maybeFuture: Option[Future[T]]): Future[Set[T]] =
      maybeFuture.fold {
        Future.successful(Set.empty[T])
      } {
        f => f.map(t => Set[T](t))
      }

    (for {
      urls <- optionFutureToFutureSet(attachmentUrl)
      emailBody <- emailBody
      messageId <- sendEmail(emailTo = Set(order.invoiceTo.email), attachmentUrls = urls, emailBody, emailSettings)
    } yield messageId)
      .map {
        messageId =>
          logger.debug(s"Email message published to SNS with messageId [$messageId]")
          Right(curr)
      }
      .recoverWith {
        case NonFatal(e) =>
          logger.error(s"Error sending documents for order: \n\n$order", e)
          Future.successful(Left(curr, e))
      }
  }

}

