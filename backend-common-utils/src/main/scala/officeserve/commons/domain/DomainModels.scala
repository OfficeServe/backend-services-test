package officeserve.commons.domain

import java.math.RoundingMode

import org.joda.money.{CurrencyUnit, IllegalCurrencyException, Money}

import scala.util.Try

case class Discount(priceDiscount: MoneyDom, deliveryDiscount: MoneyDom)

case class ItemPrice(productId: String, price: MoneyDom, discountPrice: Option[MoneyDom])

case class MoneyDom private(amount: Money) {

  def plus(other: MoneyDom):MoneyDom          = MoneyDom.asJodaMoney(amount.plus(other.amount).getAmount)
  def minus(other: MoneyDom):MoneyDom         = MoneyDom.asJodaMoney(amount.minus(other.amount).getAmount)
  def multiplyBy(multiplier: Double):MoneyDom = MoneyDom.asJodaMoney(amount.multipliedBy(multiplier, MoneyDom.roundingMode).getAmount)
  def getAmount = amount.getAmount
  def toPence = amount.getAmountMinorInt

}

case class UserDom(id: String,
                   name: String,
                   username: String,
                   email: String)

object MoneyDom {
  val roundingMode = RoundingMode.CEILING

  def asJodaMoney(amount: BigDecimal, currency: String = "GBP"): MoneyDom = {
    val currencyUnit = Try(CurrencyUnit.of(currency)).filter(_.getCurrencyCode == "GBP")
      .getOrElse(throw new IllegalCurrencyException("unsupported_currency"))
    MoneyDom(Money.of(currencyUnit, amount.bigDecimal, roundingMode))
  }

  @deprecated
  def toPence(moneyDom: MoneyDom): Int = moneyDom.toPence
}