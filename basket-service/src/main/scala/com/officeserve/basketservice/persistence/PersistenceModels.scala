package com.officeserve.basketservice.persistence

import java.time.{LocalTime, ZoneId, ZonedDateTime}
import java.util.UUID

import com.officeserve.basketservice.service.TradingDays
import com.officeserve.basketservice.web.{PaymentMethodType, PaymentRequest}
import officeserve.commons.domain.{MoneyDom, UserDom}


import scala.collection.immutable.List
import scala.util.Random

sealed trait OrderStatus {

  val status: String = getClass.getSimpleName.replace("$", "")

}

object OrderStatus {

  def fromPayment(payment: PaymentResponse): OrderStatus = payment.paymentStatus match {
    case Succeeded => Pending //TODO: Succeeded should lead to Processed status when we implement delayed payments.
    case Verified => Pending
    case Failed(_) => Started
  }

  def isCancelled(orderStatus: OrderStatus): Boolean = orderStatus == Cancelled

  def isInProgress(orderStatus: OrderStatus): Boolean = orderStatus == Started

  def isPending(orderStatus: OrderStatus): Boolean = orderStatus == Pending

  def isProcessed(orderStatus: OrderStatus): Boolean = orderStatus == Processed

  def canCancel(orderStatus: OrderStatus): Boolean = !isProcessed(orderStatus) && !isInProgress(orderStatus)

}

// Order Statuses ======================================
case object Started extends OrderStatus

case object Pending extends OrderStatus

case object Processed extends OrderStatus

case object Cancelled extends OrderStatus

// =====================================================

case class SpreedlyResponse(paymentStatus: PaymentStatus,
                            retainedToken: Boolean,
                            cardType: String,
                            truncatedCardRep: String,
                            transactionId: String,
                            gatewayTransactionId: Option[String]) {

  def toPaymentResponseModel(paymentMethod: PaymentMethod): Option[PaymentResponse] = Some(PaymentResponse(
    paymentStatus = paymentStatus,
    transactionId = Some(transactionId),
    gatewayTransactionId = gatewayTransactionId,
    paymentMethod = paymentMethod))

  def toCardPayment(token: String, toBeRetained: Boolean) =
    CardPayment(token = token,
      cardType = cardType,
      truncatedCardRep = truncatedCardRep,
      toBeRetained = toBeRetained)
}

case class CancelTransaction(transactionId: String, gatewayTransactionId: String)

case class SpreedlyRefundResponse(paymentStatus: PaymentStatus, transactionId: String, gatewayTransactionId: String) {

  def toCancelTransaction = CancelTransaction(transactionId, gatewayTransactionId)

}

case class SpreedlyRequest(orderId: String,
                           amount: Int,
                           currency: String,
                           paymentMethodToken: String,
                           retainToken: Boolean = false)

case class OrderMessage(order: Order, user: UserDom)

case class BasketItem(
                       id: String,
                       name: String,
                       productCode: String,
                       quantity: Int,
                       price: MoneyDom,
                       discountedPrice: Option[MoneyDom],
                       unitPrice: MoneyDom,
                       leadTime: Int,
                       vatRate: Double)

case class PromoSummary(deductionAmount: MoneyDom, code: String, description: String, image: Link)

case class Basket(
                   itemsCount: Int,
                   totalPrice: MoneyDom,
                   totalVAT: MoneyDom,
                   promo: Option[PromoSummary],
                   deliveryCharge: MoneyDom,
                   grandTotal: MoneyDom,
                   items: List[BasketItem]
                 )

sealed trait PaymentStatus {
  val status: String = getClass.getSimpleName.replace("$", "")
}

case object Verified extends PaymentStatus

case object Succeeded extends PaymentStatus

case class Failed(message: String) extends PaymentStatus {
  override def toString() =
    s"""[
        |  status: "$status"
        |  message: "$message"
        |]""".stripMargin
}

object Failed {

  def isFailed(paymentStatus: PaymentStatus) =
    paymentStatus.status == getClass.getSimpleName.replace("$", "")

}

case class PaymentResponse(paymentStatus: PaymentStatus,
                           transactionId: Option[String],
                           gatewayTransactionId: Option[String],
                           paymentMethod: PaymentMethod)

case class Order(id: String = UUID.randomUUID().toString, // Not shown to customers
                 userId: String,
                 basket: Basket,
                 createdDate: ZonedDateTime = ZonedDateTime.now(ZoneId.of("Europe/London")),
                 orderStatus: OrderStatus,
                 invoiceNumber: Option[Long] = None,
                 paymentReference: Option[String] = None,
                 deliveryDate: Option[ZonedDateTime] = None,
                 deliverySlot: Option[DeliverySlot] = None,
                 deliveryAddress: Option[DeliveryAddress] = None,
                 invoiceAddress: Option[InvoiceAddress] = None,
                 telephoneNum: Option[String] = None,
                 payment: Option[PaymentResponse] = None,
                 tableware: Tableware,
                 orderReference: String = Order.getOrderReference,
                 cancelTransaction: Option[CancelTransaction] = None,
                 userData: Option[UserDom] = None,
                 cutOffTime: Option[ZonedDateTime] = None) {

  val paymentFailed: Boolean = payment.exists(p => Failed.isFailed(p.paymentStatus))

  def maxLeadTime = basket.items.map(_.leadTime).foldLeft(1)(_ max _)

  def canCancel(implicit tradingDays: TradingDays) = {
    (OrderStatus.canCancel(this.orderStatus)
      && deliveryDate.exists { date =>
      val nextDeliveryFromDay = tradingDays.getDeliveryFromDay(maxLeadTime)
      date.toInstant.compareTo(nextDeliveryFromDay.toInstant) >= 0
    })
  }

  val isInProgress = OrderStatus.isInProgress(this.orderStatus)
  val isCancelled = OrderStatus.isCancelled(this.orderStatus)
  val isProcessed = OrderStatus.isProcessed(this.orderStatus)

}

case class PaymentMethod(id: String = UUID.randomUUID().toString,
                         userId: String,
                         token: Option[String] = None,
                         username: String,
                         paymentType: String,
                         isDefault: Boolean,
                         cardPayment: Option[CardPayment] = None,
                         label: String,
                         invoiceAddress: InvoiceAddress)

object PaymentMethod {
  def label(paymentType: String, cardPayment: Option[CardPayment] = None): String = {
    paymentType match {
      case PaymentMethodType.onAccountType => s"${PaymentMethodType.onAccountType}"
      case PaymentMethodType.creditCardType => cardPayment.map(c => s"${c.cardType}-${c.truncatedCardRep}").getOrElse("")
    }
  }
}


object Order {

  private def getRandomString(length: Int): String = {
    val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    Stream.continually(chars.charAt(Random.nextInt(chars.length))).take(length).mkString("")
  }

  def getOrderReference: String = s"${getRandomString(3).toUpperCase}-${Random.nextInt(10000000).toString}"

}

case class CancelledOrder(maxLeadTime: Int, order: Order)

case class Address(addressLine1: String,
                   addressLine2: Option[String] = None,
                   addressLine3: Option[String] = None,
                   postCode: String,
                   city: String,
                   label: Option[String] = None,
                   additionalInfo: Option[String] = None)

case class InvoiceAddress(companyName: Option[String] = None, fullName: String, address: Address)

case class DeliveryAddress(companyName: Option[String] = None, deliverTo: String, address: Address)

sealed trait Tableware {
  def preferenceType: String = getClass.getSimpleName.replace("$", "")

  val entitled: Int = 0
  val selected: Int = this match {
    case NoTableware(_) => 0
    case _ => entitled
  }
}


case class NoTableware(override val entitled: Int) extends Tableware

case class Napkins(override val entitled: Int) extends Tableware

case class NapkinsPlates(override val entitled: Int) extends Tableware

case class NapkinsPlatesCups(override val entitled: Int) extends Tableware

case class DeliverySlot(from: LocalTime, to: LocalTime, label: String)

object DeliverySlot {

  def apply(from: LocalTime, to: LocalTime): DeliverySlot =
    new DeliverySlot(from, to, s" ${from.toString} to ${to.toString}")

}

case class CardPayment(token: String, cardType: String, truncatedCardRep: String, toBeRetained: Boolean)


case class Link(rel: String,
                href: String,
                `type`: String
               )

case class Sequence(id: String, value: Long)

sealed trait CoverageStatus {
  val status: String = getClass.getSimpleName.replace("$", "")
}

case object Covered extends CoverageStatus

case object UpComing extends CoverageStatus

case class PostCode(area: String, areaName: String, coverageStatus: CoverageStatus, courier: Option[String], isLondonArea: Boolean)
