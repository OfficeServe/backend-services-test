package com.officeserve.basketservice.clients

import akka.actor.ActorSystem
import cc.protea.spreedly.model.{SpreedlyTransactionRequest, SpreedlyTransactionResponse}
import com.google.inject.{ImplementedBy, Inject}
import com.officeserve.basketservice.persistence.{Failed, PaymentStatus, SpreedlyRefundResponse, SpreedlyRequest, SpreedlyResponse, Succeeded}
import com.officeserve.basketservice.service.PaymentFailedException
import com.officeserve.basketservice.settings.SpreedlySettings
import com.ticketfly.spreedly.{SpreedlyClient, SpreedlyConfiguration, SpreedlyException}
import com.typesafe.scalalogging.StrictLogging
import officeserve.commons.domain.MoneyDom
import officeserve.commons.spray.webutils.Error

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import scala.util.control.NonFatal
import scalaz.syntax.std.boolean._

@ImplementedBy(classOf[PaymentGatewayImpl])
trait PaymentGateway {
  def pay(spreedlyRequest: SpreedlyRequest): Future[SpreedlyResponse]

  def refund(transactionToken: String, amount: MoneyDom): Future[SpreedlyRefundResponse]

  def verifyAndRetain(paymentMethod: String, retainOnSuccess: Boolean = false): Future[Either[Error, SpreedlyResponse]]

  def redact(paymentMethod: String): Future[Either[Error, Unit]]
}

class PaymentGatewayImpl @Inject()(spreedlySettings: SpreedlySettings) extends PaymentGateway with StrictLogging {
  implicit val ec = ExecutionContext.Implicits.global
  implicit val system = ActorSystem("spreedlyClient")
  val config = SpreedlyConfiguration(spreedlySettings.environmentToken, spreedlySettings.accessSecret)
  val spreedlyClient = new SpreedlyClient(config)

  override def pay(spreedlyRequest: SpreedlyRequest): Future[SpreedlyResponse] = {
    spreedlyClient.purchase(buildCustomRequest(spreedlyRequest, spreedlySettings.gatewayToken))
      .map(resp => extractResponse(resp, SpreedlyOperations.purchase)) recover {
      case e: SpreedlyException => {
        val message = s"payment gateway error: ${e.getMessage}"
        logger.error(message, e)
        throw PaymentFailedException(e.getMessage)
      }
    }
  }

  override def verifyAndRetain(paymentMethod: String, retainOnSuccess: Boolean = false): Future[Either[Error, SpreedlyResponse]] = {
    val request = new SpreedlyTransactionRequest()
    request.setPaymentMethodToken(paymentMethod)
    request.setRetainOnSuccess(retainOnSuccess)
    request.setGatewayAccountToken(spreedlySettings.gatewayToken)

    // verify and retain if paymentGateWay support
    spreedlyClient.verifyGatewayAccount(request) onComplete {
      case Failure(e) => logger.error("failed to verify", e)
      case Success(r) => logger.info("paymentMethod verification succcessful")
    }

    // retaining with spreedly
    spreedlyClient.retainPaymentMethod(paymentMethod)
      .map(resp => Right(extractResponse(resp, SpreedlyOperations.retainPaymentMethod))) recover {
      case e: SpreedlyException => Left(Error.withLogger(422, Some(e.errorMessage), None, e))
      case NonFatal(e) => Left(Error.withLogger(422, Some(e.getMessage), None, e))
    }
  }


  private def buildCustomRequest(paymentRequest: SpreedlyRequest, gatewayToken: String): SpreedlyTransactionRequest = {
    val request = new SpreedlyTransactionRequest()
    request.setGatewayAccountToken(gatewayToken)
    request.setPaymentMethodToken(paymentRequest.paymentMethodToken)
    request.setAmountInCents(paymentRequest.amount)
    request.setCurrencyCode(paymentRequest.currency)
    request.setRetainOnSuccess(paymentRequest.retainToken)
  }

  private def extractPaymentStatus(resp: SpreedlyTransactionResponse): PaymentStatus =
    resp.succeeded.option(Succeeded).getOrElse(Failed(s"${resp.state.name()}: ${resp.message.message}"))

  private def extractResponse(resp: SpreedlyTransactionResponse, spreedlyOperation: String): SpreedlyResponse = {
    val orderStatus = extractPaymentStatus(resp)
    SpreedlyResponse(orderStatus,
      storageState(spreedlyOperation, resp),
      resp.paymentMethod.cardType.name(),
      resp.paymentMethod.number,
      resp.token,
      Option(resp.gatewayTransactionId).filterNot(_.isEmpty))
  }

  private def storageState(operation: String, resp: SpreedlyTransactionResponse): Boolean = {
    operation match {
      case SpreedlyOperations.purchase => resp.succeeded.option(resp.retainOnSuccess)
        .getOrElse(false)
      case SpreedlyOperations.retainPaymentMethod => resp.succeeded.option(resp.getPaymentMethod.storageState.name().equals("RETAINED")).getOrElse(false)
      case _ => false
    }
  }

  object SpreedlyOperations {
    val purchase = "purchase"
    val retainPaymentMethod = "retainPaymentMethod"
  }

  override def refund(transactionToken: String, amount: MoneyDom): Future[SpreedlyRefundResponse] = {
    val req = new SpreedlyTransactionRequest()
    req.setReferenceTransactionToken(transactionToken)
    req.setGatewayAccountToken(spreedlySettings.gatewayToken)
    req.setAmountInCents(amount.toPence)
    req.setCurrencyCode(amount.amount.getCurrencyUnit.getCode)
    spreedlyClient
      .refundTransaction(req).map(r => SpreedlyRefundResponse(extractPaymentStatus(r), r.token, r.gatewayTransactionId)) recoverWith {
      case e: SpreedlyException => {
        val message = s"cancellation error: ${e.getMessage}"
        logger.error(message, e)
        Future.failed(PaymentFailedException(e.getMessage))
      }
    }
  }

  override def redact(paymentMethod: String): Future[Either[Error, Unit]] =
    spreedlyClient.redactPaymentMethod(paymentMethod, Some(spreedlySettings.gatewayToken)) map (_ => Right(())) recover {
      case e: SpreedlyException => Left(Error.withLogger(422, Some(e.errorMessage), None, e))
      case NonFatal(e) => Left(Error.withLogger(500, Some(e.getMessage), None, e))
    }
}
