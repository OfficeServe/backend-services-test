package com.officeserve.basketservice.service


import javax.inject.{Inject, Singleton}

import com.google.inject.ImplementedBy
import com.officeserve.basketservice.clients.ProductRep
import com.officeserve.basketservice.persistence.{Basket, BasketItem, OrderRepository, PromoSummary}
import com.officeserve.basketservice.service.BusinessValidator._
import com.officeserve.basketservice.settings.BasketConfig
import com.officeserve.basketservice.web._
import officeserve.commons.domain.{Discount, ItemPrice, MoneyDom}
import officeserve.commons.spray.auth.TrustedAuth
import officeserve.commons.spray.webutils.Price

import scalaz.syntax.std.boolean._
import scala.concurrent.{ExecutionContext, Future}


/**
  * Created by mo on 19/05/2016.
  */

@ImplementedBy(classOf[BasketServiceImpl])
trait BasketService {

  def calculateBasket(userId: TrustedAuth.CognitoIdentityId, basketItemRequest: BasketItemRequest, products: Seq[ProductRep]): Future[BasketServiceResponse]

}

@Singleton
class BasketServiceImpl @Inject()(priceService: PricingService,
                                  promotionService: PromotionService,
                                  basketConfig: BasketConfig
                                 ) extends BasketService with Adapter {

  implicit val ec = ExecutionContext.Implicits.global
  val VATrate = 0.2

  override def calculateBasket(userId: TrustedAuth.CognitoIdentityId, basketItemRequest: BasketItemRequest, products: Seq[ProductRep] = Seq.empty): Future[BasketServiceResponse] = {
    implicit val ec = ExecutionContext.Implicits.global
    val productMap = products.groupBy(_.id).mapValues(_.head)
    val productPrices = productMap mapValues { p =>
      ItemPrice(p.id, MoneyDom.asJodaMoney(p.price.value), p.discountedPrice.map(d => MoneyDom.asJodaMoney(d.value)))
    }

    val basketItems = basketItemRequest.items.map(item => getBasketItem(item, productPrices(item.productId), productMap(item.productId))).toList
    val totalPriceBeforeDiscount = MoneyDom.asJodaMoney(basketItems.map(b => b.discountedPrice.getOrElse(b.price).value).sum)

    val deliveryChargeBeforeDiscount = basketConfig.tableTopDeliveryCharge

    for {
      promotionResp <- basketItemRequest.promoCode.fold(Future.successful(PromotionServiceResp.emptyResponse)) {
        promoCode => promotionService.applyPromo(promoCode, totalPriceBeforeDiscount, deliveryChargeBeforeDiscount, userId)
      }

      discounts = promotionResp.discount.getOrElse(Discount(MoneyDom.asJodaMoney(0), MoneyDom.asJodaMoney(0)))

      orderTotal = totalPriceBeforeDiscount.minus(discounts.priceDiscount)
      vatOnTotalPrice = MoneyDom.asJodaMoney(basketItems.map(b => applyVATPerProductAfter(b, discounts.priceDiscount.getAmount, totalPriceBeforeDiscount.getAmount)).sum)
      priceAfterVAT = orderTotal.plus(vatOnTotalPrice)

      totalDeliveryCharge = deliveryChargeBeforeDiscount.minus(discounts.deliveryDiscount)
      vatOnDeliveryCharge = totalDeliveryCharge.multiplyBy(VATrate)
      deliveryChargeAfterVAT = totalDeliveryCharge.plus(vatOnDeliveryCharge)
      totalVAT = vatOnDeliveryCharge.plus(vatOnTotalPrice)

      grandTotal = orderTotal.plus(totalDeliveryCharge).plus(totalVAT)

      minumumOrder = for {
        min <- basketConfig.tableTopMinimumOrder
        if !meetsTableTopMinOrder(min, totalPriceBeforeDiscount)
      } yield List(ServiceHint.minimumOrderNotMet(min))

      hints = minumumOrder.getOrElse(List()) ::: promotionResp.serviceHint.getOrElse(List())

      promoSum = promotionResp.discount
        .map(d => PromoSummary(d.priceDiscount.plus(d.deliveryDiscount),
          promotionResp.promotion.get.promoCode,
          promotionResp.promotion.get.description, promotionResp.promotion.get.image))

    } yield {
      BasketServiceResponse(
        Basket(
          basketItems.size,
          Price.toPrice(totalPriceBeforeDiscount),
          Price.toPrice(totalVAT),
          promoSum,
          Price.toPrice(deliveryChargeBeforeDiscount),
          Price.toPrice(grandTotal),
          basketItems
        ),
        hints.nonEmpty.option(hints),
        basketConfig.tableTopMinimumOrder
      )
    }
  }

  private def getBasketItem(addItem: AddBasketItem, itemPrice: ItemPrice, productRep: ProductRep): BasketItem =
    BasketItem(
      id = addItem.productId,
      quantity = addItem.quantity,
      price = Price.toPrice(itemPrice.price.multiplyBy(addItem.quantity)),
      discountedPrice = itemPrice.discountPrice.map(i => Price.toPrice(i.multiplyBy(addItem.quantity))),
      leadTime = productRep.leadTime,
      name = productRep.name,
      vatRate = productRep.price.vatRate,
      productCode = productRep.productCode,
      unitPrice = itemPrice.price
    )

  private def applyVATPerProductAfter(basketItem: BasketItem, totalPromoAmount: BigDecimal, totalPrice: BigDecimal): BigDecimal = {
    val productPriceToTotalWeight = BigDecimal(basketItem.price.amount.getAmount) / totalPrice
    val productPriceToTalPromoWeigh = totalPromoAmount * productPriceToTotalWeight
    (BigDecimal(basketItem.price.amount.getAmount) - productPriceToTalPromoWeigh) * basketItem.vatRate
  }
}

case class BasketServiceResponse(basket: Basket, hints: Option[List[ServiceHint]], minimumOrderValue: Option[MoneyDom])