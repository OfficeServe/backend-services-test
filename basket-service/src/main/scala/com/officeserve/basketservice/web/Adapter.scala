package com.officeserve.basketservice.web

import com.officeserve.basketservice.persistence._
import com.officeserve.basketservice.service.{BasketServiceResponse, TradingDays}
import officeserve.commons.domain.MoneyDom
import officeserve.commons.spray.webutils.Price

import officeserve.commons.spray.webutils.DateSerializerUtil._

import scala.language.implicitConversions

/**
  * Created by mo on 29/07/2016.
  */
trait Adapter {

  //=============================  From Representation to Domain =======================================================
  implicit def fromAddressRepToAddress(a: AddressRep): Address = Address(a.addressLine1,
    a.addressLine2,
    a.addressLine3,
    a.postCode,
    a.city,
    a.label,
    a.additionalInfo)

  implicit def fromOptionAddressRepToOptionAddress(a: Option[AddressRep]): Option[Address] = a.map(fromAddressRepToAddress)

  implicit def fromPriceToMoneyDom(p: Price): MoneyDom = MoneyDom.asJodaMoney(p.value, p.currency)

  implicit def fromOptionPriceToOptionMoneyDom(p: Option[Price]): Option[MoneyDom] = p.map(fromPriceToMoneyDom)

  //====================================================================================================================

  //=============================  From Domain to Representation =======================================================
  implicit def fromMoneyDomOptionToPriceOption(m: Option[MoneyDom]): Option[Price] = m.map(fromMoneyDomToPrice)

  implicit def fromMoneyDomToPrice(m: MoneyDom): Price = Price(BigDecimal(m.amount.getAmount), m.amount.getCurrencyUnit.getCode)

  implicit def basketItemToBasketItemRep(items: List[BasketItem]): Set[BasketItemRep] = {
    items.map { b =>
      val totalVAT = b.price.multiplyBy(b.vatRate)
      BasketItemRep(b.id,
        b.name,
        b.productCode,
        b.quantity,
        b.price,
        b.discountedPrice,
        b.unitPrice,
        totalVAT,
        b.price.plus(totalVAT))
    }(collection.breakOut)
  }

  def fromOptionMoneyDomToPrice(p: Option[MoneyDom]): Price = fromMoneyDomToPrice(p.getOrElse(MoneyDom.asJodaMoney(0)))

  implicit def fromPromoSummaryToPromoRep(p: PromoSummary): PromoRep = PromoRep(p.deductionAmount, p.code, p.description, p.image)

  implicit def fromPromoSummaryOptionsPromoRepOptions(p: Option[PromoSummary]): Option[PromoRep] = p.map(fromPromoSummaryToPromoRep)

  implicit def fromBasketToBasketRep(b: Basket)(implicit hints: Option[List[ServiceHint]] = None, minimumOrderValue: Option[MoneyDom] = None): BasketRep = {
    val minimumOrderPrice: Option[Price] = minimumOrderValue
    BasketRep(b.itemsCount,
      b.totalPrice,
      b.totalVAT,
      b.promo,
      b.deliveryCharge,
      hints,
      b.grandTotal,
      b.items,
      minimumOrderPrice.getOrElse(Price(0)))
  }

  implicit def fromBasketResponseToBasketRep(resp: BasketServiceResponse): BasketRep = {
    implicit val hints = resp.hints
    implicit val minimumOrderValue = resp.minimumOrderValue
    resp.basket
  }

  implicit def fromAddressOptionToAddressRepoption(address: Option[Address]): Option[AddressRep] = {
    address.map(a => AddressRep(a.addressLine1, a.addressLine2, a.addressLine3, a.postCode, a.city, a.label, a.additionalInfo))
  }

  implicit def fromAddressToAddressRep(a: Address): AddressRep = {
    AddressRep(a.addressLine1, a.addressLine2, a.addressLine3, a.postCode, a.city, a.label, a.additionalInfo)
  }

  implicit def fromOrderMessageSeqToOrderMessageRepresentationSeq(purchase: Seq[OrderMessage])(implicit tradingDays: TradingDays): Seq[OrderMessageRep] = {
    purchase map fromOrderMessageToOrderMessageRepresentation
  }

  implicit def fromOrderMessageToOrderMessageRepresentation(purchase: OrderMessage)(implicit tradingDays: TradingDays): OrderMessageRep = {
    val order = purchase.order
    val user = purchase.user
    OrderMessageRep(
      fromOrderToOrderRep(order),
      UserRep(user.name,
        user.username,
        user.email)
    )
  }

  implicit def fromInvoiceAddressToRepresentation(d: InvoiceAddress): InvoiceAddressRep = InvoiceAddressRep(d.companyName, d.fullName, d.address)

  implicit def fromInvoiceAddressOptionToRepresentationOption(d: Option[InvoiceAddress]): Option[InvoiceAddressRep] = d.map(a => a)

  implicit def fromInvoiceAddressRepToInvoiceAddress(d: InvoiceAddressRep): InvoiceAddress = InvoiceAddress(d.companyName, d.fullName, d.address)

  implicit def fromInvoiceAddressRepOptionToInvoiceAddressOption(d: Option[InvoiceAddressRep]): Option[InvoiceAddress] = d.map(a => a)


  implicit def fromDeliveryAddressToRepresentation(d: DeliveryAddress): DeliveryAddressRep = DeliveryAddressRep(d.companyName, d.deliverTo, d.address)

  implicit def fromDeliveryAddressOptionToRepresentationOption(d: Option[DeliveryAddress]): Option[DeliveryAddressRep] = d.map(a => a)

  implicit def fromDeliveryAddressRepToDeliveryAddress(d: DeliveryAddressRep): DeliveryAddress = DeliveryAddress(d.companyName, d.deliverTo, d.address)

  implicit def fromDeliveryAddressRepOptionToDeliveryAddressOption(d: Option[DeliveryAddressRep]): Option[DeliveryAddress] = d.map(a => a)

  implicit def fromPaymentResponseToPaymentMethod(pmResponse: Option[PaymentResponse]): Option[PaymentMethodRep] = pmResponse.map(res => res.paymentMethod)

  implicit def fromOrderStatusToOrderStatusRep(o: OrderStatus): OrderStatusRep =
    OrderStatusRep(o.getClass.getSimpleName.replaceAll("\\$", ""))

  implicit def fromOrderToOrderRep(o: Order, hints: Option[List[ServiceHint]] = None)(implicit tradingDays: TradingDays): OrderRep = OrderRep(
    o.id,
    o.userId,
    fromBasketToBasketRep(o.basket)(hints),
    o.invoiceNumber,
    o.paymentReference,
    o.createdDate,
    o.deliveryDate,
    o.deliveryAddress,
    o.invoiceAddress,
    o.telephoneNum,
    o.orderStatus,
    o.tableware.entitled,
    o.tableware.preferenceType,
    o.payment,
    o.orderReference,
    o.deliverySlot.map(_.label),
    !o.isCancelled && o.canCancel,
    o.cutOffTime)

  implicit def fromOrderSeqToOrderRepSeq(os: Seq[Order])(implicit tradingDays: TradingDays): Seq[OrderRep] =
    os.map(o => fromOrderToOrderRep(o))

  implicit def fromListDeliverySlotToDeliverySlotRep(deliverySlots: List[DeliverySlot]): List[DeliverySlotRep] = {
    deliverySlots.map(s => DeliverySlotRep(s.from, s.to, s.label))
  }

  implicit def fromCardPaymentToRepresentation(c: CardPayment): CardPaymentRep =
    CardPaymentRep(token = c.token, cardType = c.cardType, truncatedCardRep = c.truncatedCardRep)

  implicit def fromCardPaymentOptionToRepresentationOption(c: Option[CardPayment]): Option[CardPaymentRep] =
    c.map(fromCardPaymentToRepresentation)

  implicit def fromPayMethodToRepresentation(p: PaymentMethod): PaymentMethodRep =
    PaymentMethodRep(
      id = p.id,
      paymentType = p.paymentType,
      isDefault = p.isDefault,
      cardPayment = p.cardPayment,
      label = p.label,
      invoiceAddress = p.invoiceAddress)

  implicit def fromPayMethodSeqToRepresentationSet(pmSq: Set[PaymentMethod]): Set[PaymentMethodRep] =
    pmSq.map(fromPayMethodToRepresentation)

  implicit def fromLinkToRepresentation(l: Link): LinkRep = LinkRep(l.rel, l.href, l.`type`)

  implicit def fromPostcodeToRepresentation(p : PostCode) = PostCodeRep(p.area,p.areaName,p.coverageStatus.status)
  //====================================================================================================================


}
