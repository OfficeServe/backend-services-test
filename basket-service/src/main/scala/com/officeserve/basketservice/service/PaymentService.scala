package com.officeserve.basketservice.service

import akka.actor.ActorRef
import com.google.inject.{ImplementedBy, Inject}
import com.officeserve.basketservice.clients.PaymentGateway
import com.officeserve.basketservice.clients.SNSPublisherActor.SendPurchaseCompleteNotification
import com.officeserve.basketservice.persistence._
import com.officeserve.basketservice.service.BusinessValidator._
import com.officeserve.basketservice.settings.BasketConfig
import com.officeserve.basketservice.web._
import com.typesafe.scalalogging.StrictLogging
import officeserve.commons.domain.{MoneyDom, UserDom}
import officeserve.commons.spray.auth.TrustedAuth.CognitoIdentityId
import officeserve.commons.spray.webutils.Error

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[PaymentServiceImpl])
trait PaymentService {
  def processPayment(orderId: String, userId: CognitoIdentityId, paymentRequest: PaymentRequest, paymentMethodService: PaymentMethodService): Future[OrderMessage]

  def refund(transactionToken: String, amount: MoneyDom): Future[SpreedlyRefundResponse]

}

class PaymentServiceImpl @Inject()(orderRepository: OrderRepository,
                                   paymentGateway: PaymentGateway,
                                   purchasePublisherActor: ActorRef,
                                   basketConfig: BasketConfig,
                                   sequenceService: SequenceService)
                                  (implicit tradingDays: TradingDays) extends PaymentService

  with StrictLogging with Adapter {
  implicit val ec = ExecutionContext.Implicits.global

  override def processPayment(orderId: String, userId: CognitoIdentityId, paymentRequest: PaymentRequest, paymentMethodService: PaymentMethodService): Future[OrderMessage] = {
    val paymentDetail = paymentRequest.paymentDetail
    val user = UserDom(userId, paymentRequest.user.name, paymentRequest.user.username, paymentRequest.user.email)

    val purchaseComplete = for {
    // get the order from the repository
      Some(order) <- orderRepository.getOrder(orderId)

      _ = if (order.userId != userId)
        throw OperationNotAllowedException("user not allowed for this operation")

      _ = if (paymentDetail.paymentReference.exists(_.length > 20) )
        throw OperationNotAllowedException("payment reference is not allowed to exceed 20 characters")

      // if the order is already processed and succeeded we don't want to proceed any further.
      _ = order.orderStatus match {
        case Processed => throw AlreadyProcessedException(s"OrderId $orderId is already processed", order)
        case _ => checkForRequiredFields(order, false)
      }

      _ = validateDeliveryDate(order.deliveryDate.get, order.maxLeadTime, tradingDays)

      // checking the required fields are set before placing the order
      _ = checkForRequiredFields(order, false)

      newInvoiceAddress = fromInvoiceAddressRepToInvoiceAddress(paymentDetail.invoiceAddress)

      paymentMethod <- getPaymentMethod(userId, user.username, paymentMethodService, paymentRequest.paymentDetail)

      // pay for the order
      paymentResponse <- paymentRequest.paymentDetail.paymentType match {
        case PaymentMethodType.onAccountType => payWithOnAccount(paymentMethod.getOrElse(throw InvalidRequestException("paymentMethod is required")),
          newInvoiceAddress)

        case PaymentMethodType.creditCardType => payWithCard(userId,
          user.username,
          order,
          newInvoiceAddress,
          paymentDetail.paymentMethodToken.getOrElse(throw InvalidRequestException("paymentMethodToken is required")),
          paymentMethod)
      }

      invoiceNumber <- paymentResponse.paymentStatus match {
        case Succeeded => sequenceService.nextValueFor("invoiceNumber").map(Some(_))
        case Verified => sequenceService.nextValueFor("invoiceNumber").map(Some(_))
        case _ => Future.successful(order.invoiceNumber)
      }

      // updating Order
      updatedOrder = updateAfterPurchase(order, invoiceNumber, paymentRequest, Some(paymentResponse))

      _ <- paymentResponse.paymentStatus match {
        case Succeeded | Verified => {
          checkForRequiredFields(updatedOrder, true)
          // persisting the updated order
          orderRepository.updateOrders(updatedOrder.copy(userData = Some(user)))
        }

        case _ => Future.successful(())
      }

      // updating the invoice address with the latest
      _ <- paymentMethod match {
        case Some(p) if Failed.isFailed(paymentResponse.paymentStatus) =>
          p.token.fold(Future.successful(())) { token =>
            paymentMethodService.redactPaymentMethod(paymentResponse.paymentMethod.id, token).map {
              case Right(_) => ()
              case Left(error) => logger.error("Failed to redact paymentMethod: " + error)
            }
          }
        case Some(p) if newInvoiceAddress != p.invoiceAddress =>
          paymentMethodService.updatePaymentMethodInvoiceAddress(paymentResponse.paymentMethod.id, paymentResponse.paymentMethod.invoiceAddress).map {
            case Right(_) => ()
            case Left(error) => throw ServiceGenericException(error)
          }
        case _ => Future.successful(())
      }

      // sending notification for successful payment
      _ = paymentResponse.paymentStatus match {
        case Succeeded => sendNotification(updatedOrder, user)
        case Verified => sendNotification(updatedOrder, user)
        case _ => ()
      }
    } yield OrderMessage(updatedOrder, user)

    purchaseComplete recover {
      case e: AlreadyProcessedException => {
        logger.warn(e.message, e)
        OrderMessage(e.order, user)
      }
    }

  }

  private def updateAfterPurchase(order: Order, invoiceNumber: Option[Long], paymentRequest: PaymentRequest, paymentResponse: Option[PaymentResponse]) =
    order.copy(
      orderStatus = paymentResponse.map(p => OrderStatus.fromPayment(p)).getOrElse(order.orderStatus),
      invoiceNumber = invoiceNumber,
      paymentReference = paymentRequest.paymentDetail.paymentReference.orElse(order.paymentReference),
      invoiceAddress = Some(paymentRequest.paymentDetail.invoiceAddress),
      payment = paymentResponse,
      tableware = paymentRequest.tableware.toModel(order)
    )


  private def getPaymentMethod(userId: String, username: String, paymentMethodService: PaymentMethodService, paymentDetail: PaymentDetail): Future[Option[PaymentMethod]] =
    for {
      pmServiceResp <- paymentMethodService.getPaymentMethods(userId = userId, username = username)
      paymentMethod = pmServiceResp match {
        case Right(pms) => if (paymentDetail.paymentType == PaymentMethodType.onAccountType) {
          val onAccountPm = pms.find(_.paymentType == PaymentMethodType.onAccountType)
          if (onAccountPm.isEmpty) throw ServiceGenericException(Error(403, Some(s"user not entitle for ${PaymentMethodType.onAccountType} "), None))
          onAccountPm
        } else {
          pms.find(_.token == paymentDetail.paymentMethodToken)
        }
        case Left(e) => throw ServiceGenericException(e)
      }
    } yield paymentMethod

  private def payWithCard(userId: String, username: String, order: Order, invoiceAddress: InvoiceAddress, paymentMethodToken: String, paymentMethod: Option[PaymentMethod]): Future[PaymentResponse] = {
    val grandTotal = order.basket.grandTotal.toPence
    val currency = order.basket.grandTotal.amount.getCurrencyUnit.getCode
    for {
      spreedlyResponse <- paymentGateway.pay(SpreedlyRequest(order.id,
        grandTotal,
        currency,
        paymentMethodToken))

      card = Some(spreedlyResponse.toCardPayment(paymentMethodToken, false))

      pm = paymentMethod.map(p => p.copy(invoiceAddress = invoiceAddress))
        .getOrElse(PaymentMethod(paymentType = PaymentMethodType.creditCardType,
          userId = userId,
          token = Some(paymentMethodToken),
          username = username,
          isDefault = false,
          cardPayment = card,
          label = PaymentMethod.label(PaymentMethodType.creditCardType, card),
          invoiceAddress = invoiceAddress))

    } yield PaymentResponse(spreedlyResponse.paymentStatus, Some(spreedlyResponse.transactionId), spreedlyResponse.gatewayTransactionId, pm)
  }

  private def payWithOnAccount(paymentMethod: PaymentMethod, invoiceAddress: InvoiceAddress): Future[PaymentResponse] =
    Future.successful(PaymentResponse(Verified, None, None, paymentMethod.copy(invoiceAddress = invoiceAddress)))


  private def sendNotification(orderDomain: Order, user: UserDom): Unit = {
    purchasePublisherActor ! SendPurchaseCompleteNotification(OrderMessage(orderDomain, user))
  }

  override def refund(transactionToken: String, amount: MoneyDom): Future[SpreedlyRefundResponse] =
    paymentGateway.refund(transactionToken, amount)

  implicit class TablewareRepExt(t: TablewareRep) {
    def toModel(order: Order): Tableware = t match {
      case NoTablewareRep => order.tableware
      case NapkinsRep => Napkins(order.tableware.entitled)
      case NapkinsPlatesRep => NapkinsPlates(order.tableware.entitled)
      case NapkinsPlatesCupsRep => NapkinsPlatesCups(order.tableware.entitled)
    }
  }

}
