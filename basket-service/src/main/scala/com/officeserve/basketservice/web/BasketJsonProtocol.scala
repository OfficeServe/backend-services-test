package com.officeserve.basketservice.web

import com.officeserve.basketservice.persistence.{Cancelled, OrderStatus, Pending, Processed, Started}
import officeserve.commons.spray.webutils.CommonJsonProtocol
import spray.json.{DeserializationException, JsObject, JsString, JsValue, RootJsonFormat}

trait
BasketJsonProtocol extends CommonJsonProtocol {

  implicit val userRepFormat = jsonFormat3(UserRep.apply)
  implicit val addressRepFormat = jsonFormat7(AddressRep)
  implicit val orderStatusRep = jsonFormat1(OrderStatusRep)
  implicit val invoiceAddressRepFormat = jsonFormat3(InvoiceAddressRep)
  implicit val deliveryAddressRepFormat = jsonFormat3(DeliveryAddressRep)
  implicit val serviceHintFormat = jsonFormat2(ServiceHint.apply)
  implicit val addItemFormat = jsonFormat2(AddBasketItem)
  implicit val basketRequestFormat = jsonFormat2(BasketItemRequest)
  implicit val cardPaymentRepFormat = jsonFormat3(CardPaymentRep)
  implicit val createPaymentMethodFormat = jsonFormat4(CreateOrUpdatePaymentMethod)
  implicit val paymentDetailFormat = jsonFormat4(PaymentDetail)
  implicit val basketItemRepFormat = jsonFormat9(BasketItemRep)
  implicit val linkRepFormat = jsonFormat3(LinkRep)
  implicit val promoRepFormat = jsonFormat4(PromoRep)
  implicit val basketRepFormat = jsonFormat9(BasketRep)
  implicit val deliverySlotRepFormat = jsonFormat3(DeliverySlotRep)
  implicit val blockDateRepFormat = jsonFormat4(BlockDateRep)
  implicit val updateFieldFormat = jsonFormat4(UpdateField)
  implicit val updateOrderFormat = jsonFormat2(UpdateOrder)
  implicit val postCodeFormat = jsonFormat3(PostCodeRep)
  implicit val deliveryResponseRep = jsonFormat2(DeliveryResponseRep)

  implicit object OrderStatusFormat extends RootJsonFormat[OrderStatus] {

    override def read(json: JsValue): OrderStatus = json.asJsObject.getFields("status") match {
      case Seq(JsString(status)) if Processed.status == status => Processed
      case Seq(JsString(status)) if Started.status == status => Started
      case Seq(JsString(status)) if Cancelled.status == status => Cancelled
      case Seq(JsString(status)) if Pending.status == status => Pending
      case _ => throw new DeserializationException(s"Error deserialising ${json.prettyPrint}")
    }

    override def write(data: OrderStatus): JsValue = data match {
      case s => JsObject("status" -> JsString(s.status))
    }
  }

  implicit object TablewareFormat extends RootJsonFormat[TablewareRep] {

    override def read(json: JsValue): TablewareRep = json match {
      case JsString("NO_TABLEWARE") => NoTablewareRep
      case JsString("NAPKINS") => NapkinsRep
      case JsString("NAPKINS_PLATES") => NapkinsPlatesRep
      case JsString("NAPKINS_PLATES_CUPS") => NapkinsPlatesCupsRep
      case _ => throw new DeserializationException(s"Error deserialising tableware")
    }

    override def write(data: TablewareRep): JsValue = data match {
      case s => JsObject("tableware" -> JsString(s.name))
    }
  }

  implicit val paymentRequestFormat = jsonFormat3(PaymentRequest)
  implicit val pageSummaryResponseFormat = jsonFormat3(PageSummaryResponse)
  implicit val paymentMethodRepFormat = jsonFormat6(PaymentMethodRep)
  implicit val orderResponseFormat = jsonFormat18(OrderRep)
  implicit val pageResponseFormat = jsonFormat2(PageResponse.apply[OrderRep])
  implicit val startOrderRepFormat = jsonFormat2(StartOrderRep)
  implicit val paymentMethodResponseRepFormat: RootJsonFormat[PaymentMethodResponseRep] = jsonFormat2(PaymentMethodResponseRep.apply)

}
