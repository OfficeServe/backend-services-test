package com.officeserve.basketservice.service

import java.time.ZonedDateTime

import com.officeserve.basketservice.persistence._
import com.officeserve.basketservice.web.PaymentMethodType
import officeserve.commons.domain.MoneyDom
import officeserve.commons.spray.webutils.DateSerializerUtil
import officeserve.commons.spray.webutils.Error
import spray.http.StatusCodes._
import com.officeserve.basketservice.web.DeliveryAddressRep

import scala.concurrent.{ExecutionContext, Future}

object BusinessValidator {
  implicit val ec = ExecutionContext.Implicits.global

  def meetsTableTopMinOrder(minimumOrderValue: MoneyDom, total: MoneyDom): Boolean =
    !total.amount.isLessThan(minimumOrderValue.amount)

  def checkForRequiredFields(order: Order, purchaseCompleted: Boolean): Unit = {
    val options = if (purchaseCompleted) {
      Map("transactionId" -> order.payment.map(_.transactionId),
        "gatewayTransactionId" -> order.payment.map(_.gatewayTransactionId),
        "deliveryAddress" -> order.deliveryAddress,
        "deliveryDate" -> order.deliveryDate,
        "telephoneNum" -> order.telephoneNum,
        "payment" -> order.payment,
        "invoiceNumber" -> order.invoiceNumber)
    } else {
      Map("deliveryAddress" -> order.deliveryAddress,
        "deliveryDate" -> order.deliveryDate,
        "telephoneNum" -> order.telephoneNum)
    }
    val errors = options.collect {
      case f if f._2.isEmpty && !order.paymentFailed => f._1 + " is missing"
    }
    if (errors.nonEmpty)
      throw new InvalidRequestException(errors.toSeq: _*)
  }

  def validateDeliveryDate(deliveryDate: ZonedDateTime, maxLeadTime: Int, tradingDays: TradingDays): Unit = {
    // check if the delivery data falls on a weekend
    if (tradingDays.isWeekend(deliveryDate)) throw InvalidRequestException("invalid delivery date: we don't deliver on the Weekend")

    // check if the delivery data falls on a holiday
    if (tradingDays.isHoliday(deliveryDate)) throw InvalidRequestException("invalid delivery date: non trading date selected")

    // check if deliveryDate is before the available from Date
    val fromDate = tradingDays.getDeliveryFromDay(maxLeadTime)
    if (deliveryDate.isBefore(fromDate)) throw InvalidRequestException("invalid delivery date: delivery should not be before the available from Date : " + DateSerializerUtil.zonedDateTimeToString(fromDate))
  }

  def validateTelephoneNumFormat(tel: String): Unit = {
    if (!tel.forall(_.isDigit) || tel.length != 11) throw InvalidRequestException("invalid telephone number format")
  }

  def validateDeliveryPostcode(deliveryAddress: Option[DeliveryAddressRep], deliveryService: DeliveryService): Future[Either[Error, Unit]] =
    deliveryAddress.map { d =>
      deliveryService.getPostcodeArea(d.address.postCode).map {
        case Right(p) => if (p.coverageStatus != Covered) {
          Left(Error(BadRequest.intValue, Some(s"postcode_status_${p.coverageStatus.status}"), None))
        } else {
          Right(())
        }
        case Left(e) => Left(e)
      }
    }.getOrElse(Future.successful(Right(())))

}
