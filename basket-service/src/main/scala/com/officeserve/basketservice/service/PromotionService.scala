package com.officeserve.basketservice.service


import com.google.inject.{ImplementedBy, Inject}
import com.officeserve.basketservice.persistence.{Link, OrderRepository}
import com.officeserve.basketservice.web.{ServiceHint, UserRep}
import officeserve.commons.domain.{Discount, MoneyDom}
import officeserve.commons.spray.auth.TrustedAuth

import scala.concurrent.{ExecutionContext, Future}
import scalaz.syntax.std.boolean._

@ImplementedBy(classOf[PromotionServiceImpl])
trait PromotionService {

  def applyPromo(promoCode: String, totalPrice: MoneyDom, totalDeliveryCharge: MoneyDom, userId: TrustedAuth.CognitoIdentityId): Future[PromotionServiceResp]

}

class PromotionServiceImpl @Inject()(orderRepository: OrderRepository, promoRepository: PromoRepository) extends PromotionService {
  implicit val ec = ExecutionContext.Implicits.global

  override def applyPromo(promoCode: String, totalPrice: MoneyDom, totalDeliveryCharge: MoneyDom, userId: TrustedAuth.CognitoIdentityId): Future[PromotionServiceResp] =
    promoRepository.promotions.find(p => p.promoCode.equalsIgnoreCase(promoCode)) match {
      case Some(promo) => {
        promo match {
          case p: Promo25PercentOnFirstOrder =>
            if (userId == UserRep.unknownUserId) {
              Future.successful(PromotionServiceResp(Some(p.getDiscount(totalPrice, MoneyDom.asJodaMoney(0))),
                Some(p),
                Some(List(ServiceHint(ServiceHint.promotion_applied_for_unknown_user_key, "promotion applied for un unknown user")))))
            }
            else {
              for {
                orders <- orderRepository.getUserOrders(userId) map (_.filterNot(_.isInProgress)) map (_.filterNot(_.isCancelled))
                promoResp = orders.isEmpty.option(PromotionServiceResp(Some(p.getDiscount(totalPrice, MoneyDom.asJodaMoney(0))), Some(p), None))
                  .getOrElse(PromotionServiceResp(None, None, Some(List(ServiceHint(ServiceHint.promotion_already_redeemed_key, "voucher already redemmed")))))
              } yield promoResp
            }
          case _ => Future.successful(PromotionServiceResp(Some(promo.getDiscount(totalPrice, totalDeliveryCharge)), Some(promo), None))

        }
      }
      case None => Future.successful(PromotionServiceResp(None, None, Some(List(ServiceHint(ServiceHint.promotion_not_found_key, "promotion not found")))))
    }
}

abstract class Promotion {
  val promoCode: String
  val discountAmount: Double
  val description: String
  val promoType: PromoType
  val image: Link

  def getDiscount(totalPrice: MoneyDom, totalDelivery: MoneyDom): Discount

  protected def calculateDiscount(totalPrice: Option[MoneyDom], totalDelivery: Option[MoneyDom]) = promoType match {
    case PercentageOffPromoType => Discount(totalPrice.getOrElse(MoneyDom.asJodaMoney(0)).multiplyBy(discountAmount),
      totalDelivery.getOrElse(MoneyDom.asJodaMoney(0)).multiplyBy(discountAmount))

    case AmountOffPromoType => Discount(MoneyDom.asJodaMoney(totalPrice.isDefined.option[BigDecimal](discountAmount).getOrElse(0)),
      MoneyDom.asJodaMoney(totalDelivery.isDefined.option[BigDecimal](discountAmount).getOrElse(0)))
  }
}

abstract class Promo25PercentOnFirstOrder(code: String) extends Promotion {
  override val discountAmount = 0.25
  override val promoCode = code
  override val description = "25% first order discount"
  override val promoType = PercentageOffPromoType
  override val image = Link("image", s"https://images.contentful.com/s5khr7w5elfa/1rIMvlkAtKEy2C2MSKm2cY/d54883983e6c3a86bdbe06dd0db1f6bf/welcome25.png", "image/png")

  override def getDiscount(totalPrice: MoneyDom, totalDelivery: MoneyDom = MoneyDom.asJodaMoney(0)): Discount = calculateDiscount(Some(totalPrice), None)
}

case object Welcome25 extends Promo25PercentOnFirstOrder("WELCOME25")

case object Hello25 extends Promo25PercentOnFirstOrder("HELLO25")

case object PaLife25 extends Promo25PercentOnFirstOrder("PALIFE25")

case object Paclub25 extends Promo25PercentOnFirstOrder("PACLUB25")

case object Launch25 extends Promo25PercentOnFirstOrder("LAUNCH25")

case object GlobalPa25 extends Promo25PercentOnFirstOrder("GLOBALPA25")

case object FruitDrop25 extends Promo25PercentOnFirstOrder("FRUITDROP25")

case object PostCard25 extends Promo25PercentOnFirstOrder("POSTCARD25")

case object PromoCustomer25 extends Promotion {
  override val discountAmount = 0.25
  override val promoCode = "CUSTOMER25"
  override val description = "25% discount"
  override val promoType = PercentageOffPromoType
  override val image = Link("image", s"https://images.contentful.com/s5khr7w5elfa/1rIMvlkAtKEy2C2MSKm2cY/d54883983e6c3a86bdbe06dd0db1f6bf/welcome25.png", "image/png")

  override def getDiscount(totalPrice: MoneyDom, totalDelivery: MoneyDom = MoneyDom.asJodaMoney(0)): Discount = calculateDiscount(Some(totalPrice), None)
}


sealed trait PromoType

object AmountOffPromoType extends PromoType

object PercentageOffPromoType extends PromoType

case class PromotionServiceResp(discount: Option[Discount], promotion: Option[Promotion], serviceHint: Option[List[ServiceHint]])

object PromotionServiceResp {
  val emptyResponse = PromotionServiceResp(None, None, None)
}

@ImplementedBy(classOf[PromoRepositoryImpl])
trait PromoRepository {
  val promotions: Set[Promotion]
}

class PromoRepositoryImpl @Inject() extends PromoRepository {
  override val promotions: Set[Promotion] = Set(PromoCustomer25, Welcome25, Hello25, PaLife25, Paclub25, Launch25, GlobalPa25, FruitDrop25, PostCard25)
}
