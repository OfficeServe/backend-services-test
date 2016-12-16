package com.officeserve.basketservice.web

import java.time.ZonedDateTime

import officeserve.commons.domain.MoneyDom
import officeserve.commons.spray.webutils.{Error, Price}
import org.joda.money.CurrencyUnit

// ============================== requests ============================================================================

case class BasketItemRequest(promoCode: Option[String], items: Set[AddBasketItem])


case class PaymentRequest(paymentDetail: PaymentDetail,
                          user: UserRep,
                          tableware: TablewareRep)

case class UpdateOrder(operation: String, fields: UpdateField) {
  require(operation == UpdateOperation.replace)
}

case class CreateOrUpdatePaymentMethod(token: Option[String], invoiceAddress: InvoiceAddressRep, username: String, isDefault: Option[Boolean] = Some(false))

// ============================== responses ============================================================================

case class PageSummaryResponse(total: Int, pageSize: Int, offset: Int)

case class PageResponse[T](summary: PageSummaryResponse, data: Seq[T])

object PageResponse {

  def apply[T](data: Seq[T]): PageResponse[T] =
    apply(PageSummaryResponse(data.size, data.size, 0), data)
}

case class StartOrderRep(order: OrderRep, blockDate: BlockDateRep)

case class PaymentMethodResponseRep(success: Option[Set[PaymentMethodRep]], errors: Option[List[Error]])

object PaymentMethodResponseRep {
  val emptyResponse = PaymentMethodResponseRep(None, None)
}

case class DeliveryResponseRep(success: Option[List[PostCodeRep]], errors: Option[List[Error]])

// ==================================Models ============================================================================

case class PaymentMethodRep(id: String,
                            paymentType: String,
                            isDefault: Boolean,
                            cardPayment: Option[CardPaymentRep],
                            label: String,
                            invoiceAddress: InvoiceAddressRep) {
  require(paymentType == PaymentMethodType.creditCardType || paymentType == PaymentMethodType.onAccountType,
    s"paymentMethod should be either ${PaymentMethodType.onAccountType} or ${PaymentMethodType.creditCardType}")
}

object PaymentMethodType {
  val onAccountType = "ON_ACCOUNT"
  val creditCardType = "CREDIT_CARD"
}

case class CardPaymentRep(token: String, cardType: String, truncatedCardRep: String)

case class UpdateField(deliveryDate: Option[ZonedDateTime],
                       deliveryAddress: Option[DeliveryAddressRep],
                       telephoneNum: Option[String],
                       paymentReference: Option[String])

case class PaymentDetail(paymentType: String, paymentMethodToken: Option[String], invoiceAddress: InvoiceAddressRep, paymentReference: Option[String] = None) {
  require(paymentType == PaymentMethodType.creditCardType || paymentType == PaymentMethodType.onAccountType,
    s"paymentMethod should be either ${PaymentMethodType.onAccountType} or ${PaymentMethodType.creditCardType}")
  if (paymentType == PaymentMethodType.creditCardType) require(paymentMethodToken.isDefined,
    s"paymentMethodToken should be defined for paymentType ${PaymentMethodType.creditCardType} ")
}

case class AddressRep(addressLine1: String,
                      addressLine2: Option[String],
                      addressLine3: Option[String],
                      postCode: String,
                      city: String,
                      label: Option[String] = None,
                      additionalInfo: Option[String] = None)

case class InvoiceAddressRep(companyName: Option[String] = None, fullName: String, address: AddressRep)

case class DeliveryAddressRep(companyName: Option[String] = None, deliverTo: String, address: AddressRep)

case class BasketItemRep(productId: String,
                         name: String,
                         productCode: String,
                         quantity: Int,
                         price: Price,
                         discountedPrice: Option[Price],
                         unitPrice: Price,
                         totalVat: Price,
                         totalPrice: Price)

case class PromoRep(deductionAmount: Price, code: String, description: String, image: LinkRep)

case class BasketRep(itemsCount: Int,
                     totalPrice: Price,
                     totalVAT: Price,
                     promotion: Option[PromoRep],
                     deliveryCharge: Price,
                     hints: Option[List[ServiceHint]],
                     grandTotal: Price,
                     items: Set[BasketItemRep],
                     minimumOrderValue: Price)

case class OrderStatusRep(status: String)

case class OrderRep(id: String,
                    userId: String,
                    basket: BasketRep,
                    invoiceNumber: Option[Long],
                    paymentReference: Option[String] = None,
                    createdDate: ZonedDateTime,
                    deliveryDate: Option[ZonedDateTime],
                    deliveryAddress: Option[DeliveryAddressRep],
                    invoiceAddress: Option[InvoiceAddressRep],
                    telephoneNum: Option[String],
                    orderStatus: OrderStatusRep,
                    tableware: Int,
                    tablewareType: String,
                    paymentMethod: Option[PaymentMethodRep],
                    orderReference: String,
                    deliverySlot: Option[String],
                    isCancellable: Boolean,
                    cutOffTime: Option[ZonedDateTime] = None)

case class UserRep(name: String,
                   username: String,
                   email: String)

case class AddBasketItem(
                          productId: String,
                          quantity: Int
                        )

case class ServiceHint(name: String, hint: String)

object ServiceHint {
  // promotions
  val promotion_already_redeemed_key = "promotion_already_redeemed"
  val promotion_not_found_key = "promotion_not_found"
  val promotion_applied_for_unknown_user_key = "promotion_applied_for_unknown_user"

  // basket minimum Order
  def minimumOrderWarning(minimumOrderValue: String) = s"We’re sorry, your order doesn’t meet our minimum order value of ${minimumOrderValue} (excl. VAT). Please continue shopping."

  val basketMinimumOrder_key = "minimumOrder"

  //

  def minimumOrderNotMet(minimumOrderValue: MoneyDom) =
    ServiceHint(basketMinimumOrder_key,
      minimumOrderWarning(s"${getCurrencySymbol(minimumOrderValue)}${minimumOrderValue.amount.getAmount.toString.replaceAll("()\\.0+$|(\\..+?)0+$", "$2")}"))

  private def getCurrencySymbol(moneyDom: MoneyDom): String = moneyDom.amount.getCurrencyUnit match {
    case CurrencyUnit.GBP => "£"
    case  _ => ""
  }

}

case class OrderMessageRep(order: OrderRep, user: UserRep)

sealed trait TablewareRep {

  def name = getClass.getSimpleName.replace("$", "") match {
    case "NoTableware" => "NO_TABLEWARE"
    case "Napkins" => "NAPKINS"
    case "NapkinsPlates" => "NAPKINS_PLATES"
    case "NapkinsPlatesCups" => "NAPKINS_PLATES_CUPS"
  }
}

object NoTablewareRep extends TablewareRep

object NapkinsRep extends TablewareRep

object NapkinsPlatesRep extends TablewareRep

object NapkinsPlatesCupsRep extends TablewareRep

object UpdateOperation {
  val replace = "REPLACE"
}

case class DeliverySlotRep(fromTime: String, toTime: String, label: String)

case class BlockDateRep(fromDate: ZonedDateTime, holidays: List[ZonedDateTime], deliverySlots: List[DeliverySlotRep], tableTopCutOffTime: ZonedDateTime)

case class LinkRep(rel: String,
                   href: String,
                   `type`: String)

object UserRep {
  val unknownUserId = "Fd4t8m7lyL1HM9l5BOQcNFpKB4kT6A5O"
}

case class PostCodeRep(area: String, areaName: String, coverageStatus: String)