package com.officeserve.basketservice.service

import com.google.inject.{ImplementedBy, Inject}
import com.officeserve.basketservice.web.AddBasketItem
import officeserve.commons.domain.{ItemPrice, MoneyDom}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

@ImplementedBy(classOf[MockPricingServiceImpl])
trait PricingService {
  def getProductPrices(basketItems: Set[AddBasketItem]): Future[Set[ItemPrice]]

}

/** Each product is given a random price of between 5 and 15 pounds. */
class MockPricingServiceImpl @Inject() extends PricingService {
  implicit val ec = ExecutionContext.Implicits.global

  override def getProductPrices(addItems: Set[AddBasketItem]): Future[Set[ItemPrice]] = Future {
    val priceAmount: Double = 5 + Random.nextInt(10);
    val discountAmount: Double = priceAmount - (priceAmount * 0.15)
    for {
      item <- addItems
    } yield ItemPrice(item.productId, MoneyDom.asJodaMoney(priceAmount), Some(MoneyDom.asJodaMoney(discountAmount)))
  }
}
