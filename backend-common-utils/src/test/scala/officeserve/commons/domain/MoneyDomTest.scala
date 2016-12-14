package officeserve.commons.domain

import org.scalatest.{FeatureSpec, GivenWhenThen, Matchers}

class MoneyDomTest extends FeatureSpec with GivenWhenThen with Matchers {
  info(" MoneyDom should round up decimals values")

  feature("Rounding for BigDecimal value") {
    scenario("Ammount Should round up to 2 decimal scale when third decimal is present") {
      Given("A bigDecimal number")
      val value = 3.4506

      When("I invoke MoneyDom object asJodaMoney method")
      val money = MoneyDom.asJodaMoney(value).amount

      Then("Value should be rounding up using Ceiling mode")
      assert(BigDecimal(money.getAmount) == 3.46)
      assert(money.getCurrencyUnit.getCode == "GBP")
    }
  }

  feature("convert to pence") {
    scenario("Ammount Should convert amount to pence") {
      Given("I have a MoneyDom.asJodaMoney")
      val value = MoneyDom.asJodaMoney(3.45)

      When("I invoke MoneyDom object toPence method")
      val money =value.toPence

      Then("Value should be converted to pence")
      assert(money == 345)
    }
  }

}
