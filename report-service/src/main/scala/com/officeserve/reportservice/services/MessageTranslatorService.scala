package com.officeserve.reportservice.services

import java.net.URL
import java.text.DecimalFormat
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import com.officeserve.basketservice.web.{OrderMessageRep, OrderRep}
import com.officeserve.documentservice.models.{ProductReport => ProductReportRep, _}
import com.officeserve.reportservice.models._
import com.officeserve.reportservice.models.productreport.{ProductReport, ProductReportData}
import com.officeserve.sendemail.model.{EmailFormat, Html, Txt}
import officeserve.commons.spray.webutils.Price
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.json4s.jackson.Serialization.write

import scala.annotation.implicitNotFound
import scala.util.Try

trait MessageTranslatorService {
  def fromMessageBodyToEvent(message: String): Try[Event[EventType, OrderMessageRep]]

  def fromEventToMessageBody[T <: EventType, E](event: Event[T, E]): String

  def fromOrderMessageRepToOrder(orderMessageRep: OrderMessageRep): Try[Order]

  def fromProductReportToProductReportRep(rawProductReport: ProductReport): ProductReportRep

}

class MessageTranslatorServiceImpl() extends MessageTranslatorService {

  import MessageTranslatorService._

  private implicit val formats = DefaultFormats + EventTypeSerializer + ZDTSerializer + EmailFormatSerializer + URLSerializer

  override def fromMessageBodyToEvent(message: String): Try[Event[EventType, OrderMessageRep]] = {
    val messageJson = Try(parse(message))

    (for {
      json <- messageJson
      snsMessage <- Try(json.extract[SnsMessage])
      event <- Try(parse(snsMessage.Message).extract[Event[EventType, OrderMessageRep]])
    } yield event)
      .orElse {
        for {
          json <- messageJson
          event <- Try(json.extract[Event[EventType, OrderMessageRep]])
        } yield event
      }
  }

  override def fromEventToMessageBody[T <: EventType, E](event: Event[T, E]): String = {
    write(event)
  }

  override def fromOrderMessageRepToOrder(orderMessageRep: OrderMessageRep): Try[Order] = {
    implicit val formattableZDT = Formatter.FormattableZonedDateTime
    implicit val formattablePrice = Formatter.FormattablePrice

    Try {
      val orderRep = orderMessageRep.order
      val userRep = orderMessageRep.user

      val invoiceAddressRep = orderRep.paymentMethod.get.invoiceAddress
      val deliveryAddressRep = orderRep.deliveryAddress.get
      val basketRep = orderRep.basket

      val invoiceAddress = Address(
        addressLine1 = invoiceAddressRep.address.addressLine1,
        addressLine2 = invoiceAddressRep.address.addressLine2,
        addressLine3 = invoiceAddressRep.address.addressLine3,
        postCode = invoiceAddressRep.address.postCode,
        city = invoiceAddressRep.address.city,
        additionalInfo = invoiceAddressRep.address.additionalInfo
      )
      val invoiceTo = InvoiceTo(
        name = invoiceAddressRep.fullName,
        address = invoiceAddress,
        phone = orderRep.telephoneNum.get,
        email = userRep.email
      )

      val invoiceDetails = InvoiceDetails(
        date = Formatter.format(orderRep.createdDate),
        invoiceNumber = orderRep.invoiceNumber.get.toString,
        companyName = invoiceAddressRep.companyName,
        contactName = invoiceAddressRep.fullName,
        paymentReference = orderRep.paymentReference,
        accountNumber = None,
        paymentMethod = orderRep.paymentMethod.get.paymentType,
        shippingMethod = None
      )

      val address = Address(
        addressLine1 = deliveryAddressRep.address.addressLine1,
        addressLine2 = deliveryAddressRep.address.addressLine2,
        addressLine3 = deliveryAddressRep.address.addressLine3,
        postCode = deliveryAddressRep.address.postCode,
        city = deliveryAddressRep.address.city,
        additionalInfo = deliveryAddressRep.address.additionalInfo
      )
      val deliveryAddress = DeliveryAddress(
        companyName = deliveryAddressRep.companyName,
        name = deliveryAddressRep.deliverTo,
        address
      )

      val deliveryDate = documentDateFormatter.format(orderRep.deliveryDate.get)

      val deliverySlot = orderRep.deliverySlot.get.trim


      val orderTotal = basketRep.totalPrice.copy(
        value = basketRep.totalPrice.value - basketRep.promotion.fold(BigDecimal(0))(_.deductionAmount.value) + basketRep.deliveryCharge.value
      )

      val basket = Basket(
        totalPrice = Formatter.format(basketRep.totalPrice),
        deliveryCharge = Formatter.format(basketRep.deliveryCharge),
        totalVat = Formatter.format(basketRep.totalVAT),
        orderTotal = Formatter.format(orderTotal),
        invoiceTotal = Formatter.format(basketRep.grandTotal),
        promo = basketRep.promotion.map { p =>
          Promo(p.description, Formatter.format(p.deductionAmount.copy(value = -p.deductionAmount.value)))
        },
        items = basketRep.items.map {
          basketItemRep =>
            Item(
              name = basketItemRep.name,
              productCode = basketItemRep.productCode,
              quantity = basketItemRep.quantity.toString,
              unitPrice = Formatter.format(basketItemRep.unitPrice),
              totalPrice = Formatter.format(basketItemRep.price),
              totalVat = Formatter.format(basketItemRep.totalVat),
              total = Formatter.format(basketItemRep.totalPrice)
            )
        }.toList
      )

      Order(invoiceTo, invoiceDetails, deliveryAddress, Some(deliveryDate), deliverySlot, basket, createTableware(orderRep))
    }
  }

  def createTableware(orderRep: OrderRep): Option[Tableware] = {
    orderRep.tablewareType match {
      case "Napkins"            => Some(Tableware(orderRep.tableware, 0, 0))
      case "NapkinsPlates"      => Some(Tableware(orderRep.tableware, 0, orderRep.tableware))
      case "NapkinsPlatesCups"  => Some(Tableware(orderRep.tableware, orderRep.tableware, orderRep.tableware))
      case "NoTableware"        => None
      case _                    => None
    }
  }

  override def fromProductReportToProductReportRep(rawProductReport: ProductReport): ProductReportRep =
    ProductReportRep(
      date = dateAndTimeFormatter.format(rawProductReport.generatedOn),
      items = rawProductReport.report.values.map { productionReportEntry =>
        ProductReportItem(
          productCode = productionReportEntry.productCode,
          productName = productionReportEntry.productName,
          previous = productionReportEntry.previousQuantity.toString,
          difference = (productionReportEntry.currentQuantity - productionReportEntry.previousQuantity).toString,
          total = productionReportEntry.currentQuantity.toString,
          deliveryDate = dateAndTimeFormatter.format(productionReportEntry.deliveryDate)
        )
      }.toList
    )
}

object MessageTranslatorService {
  val documentDateFormatter = DateTimeFormatter.ofPattern("E d MMM yyyy")

  val dateAndTimeFormatter = DateTimeFormatter.ofPattern("E d MMM yyyy HH:mm")

  // SNS message wrapper. This is how we read from SNS -> SQS
  case class SnsMessage(Type: String,
                        MessageId: String,
                        TopicArn: String,
                        Message: String,
                        Timestamp: String,
                        SignatureVersion: String,
                        Signature: String,
                        SigningCertURL: String,
                        UnsubscribeURL: String
                       )

  object EventTypeSerializer extends CustomSerializer[EventType](format => ( {
    case JString(eventType) => eventType match {
      case "PurchaseComplete" => PurchaseComplete
      case "PurchaseCancelled" => PurchaseCancelled
      case "CutOffTimeProcessOrders" => CutOffTimeProcessOrders
      case "StoreOrders" => StoreOrders
      case "SendOrderDocuments" => SendOrderDocuments
      case "SendEmail" => SendEmail
      case "PartialProductReportTime" => PartialProductReportTime
      case "SendCancellationDocuments" => SendCancellationDocuments
      case "CutOffTime" => CutOffTime
    }
  }, {
    case PurchaseComplete => JString("PurchaseComplete")
    case PurchaseCancelled => JString("PurchaseCancelled")
    case CutOffTimeProcessOrders => JString("CutOffTimeProcessOrders")
    case StoreOrders => JString("StoreOrders")
    case SendOrderDocuments => JString("SendOrderDocuments")
    case SendEmail => JString("SendEmail")
    case PartialProductReportTime => JString("PartialProductReportTime")
    case SendCancellationDocuments => JString("SendCancellationDocuments")
    case CutOffTime => JString("CutOffTime")
  }))

  object EmailFormatSerializer extends CustomSerializer[EmailFormat](format => ( {
    case JString(emailFormat) => emailFormat.toLowerCase match {
      case "html" => Html
      case "txt" => Txt
    }
  }, {
    case Html => JString("html")
    case Txt => JString("txt")
  }))

  object URLSerializer extends CustomSerializer[URL](format => ( {
    case JString(url) => new URL(url)
  }, {
    case url: URL => JString(url.toString)
  }))

  object ZDTSerializer extends CustomSerializer[ZonedDateTime](format => ( {
    case JString(s) => ZonedDateTime.parse(s)
  }, {
    case zdt: ZonedDateTime => JString(zdt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")))
  }))


  object Formatter {

    @implicitNotFound("No member of type class Formattable in scope for ${T}")
    trait Formattable[T] {
      def format(x: T): String
    }

    implicit object FormattablePrice extends Formattable[Price] {
      private val gbpFormat = new DecimalFormat("£0.00")

      override def format(x: Price): String = x.currency match {
        case "GBP" => gbpFormat.format(x.value)
        case _ => s"${x.value} ${x.currency}"
      }
    }

    implicit object FormattableZonedDateTime extends Formattable[ZonedDateTime] {
      override def format(x: ZonedDateTime): String = x.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
    }

    def format[T: Formattable](x: T): String = implicitly[Formattable[T]].format(x)

  }

}
