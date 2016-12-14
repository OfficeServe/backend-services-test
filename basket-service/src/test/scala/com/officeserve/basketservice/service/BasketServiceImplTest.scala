package com.officeserve.basketservice.service

import com.google.inject.Guice
import com.officeserve.basketservice.AppModule
import com.officeserve.basketservice.clients.{ProductPriceRep, ProductRep, UnitOfMeasurementRep}
import com.officeserve.basketservice.persistence.{Address, Basket, BasketItem, DeliveryAddress, DeliverySlot, Link, NoTableware, Order, OrderRepository, Processed, PromoSummary, Started}
import com.officeserve.basketservice.service.BasketServiceImplTest.{DummyPromoRepository, TestAmountOffPromo, TestPercentageOffPromo, TestPromoOnDelivery}
import com.officeserve.basketservice.settings.{BasketConfig, BasketSettings}
import com.officeserve.basketservice.web.{AddBasketItem, BasketItemRequest, ServiceHint}
import net.codingwell.scalaguice.InjectorExtensions._
import officeserve.commons.domain.{Discount, MoneyDom}
import officeserve.commons.spray.webutils.DateSerializerUtil
import org.mockito.Mockito.{when => mockitoWhen}
import org.mockito.{Matchers => mockMatchers}
import org.scalatest._
import org.scalatest.mock.MockitoSugar

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.Random

class BasketServiceImplTest extends FeatureSpec with GivenWhenThen with BasketServiceImplFixture {
  info("As a basket owner")
  info("I want my basket to be calculated")

  feature("Apply different types of promotions to basket") {

    scenario("Apply a percentage promotion") {
      Given("a basket request with a percentage voucher code")
      val basketPercentageRequest = BasketItemRequest(Some(TestPercentageOffPromo.promoCode), addBasketItems)

      When("apply percentage promo is invoked")
      val resp = Await.result(basketService.calculateBasket("someUser", basketPercentageRequest, products), 2 seconds)

      Then("percentage promotion should be applied to the total price with decimal rounded up")
      val expectedBasket = Basket(addBasketItems.size,
        totalPrice = MoneyDom.asJodaMoney(43.38),
        totalVAT = MoneyDom.asJodaMoney(5.39),
        promo = Some(PromoSummary(MoneyDom.asJodaMoney(10.85), TestPercentageOffPromo.promoCode, TestPercentageOffPromo.description, TestPercentageOffPromo.image)),
        deliveryCharge = MoneyDom.asJodaMoney(8.00),
        grandTotal = MoneyDom.asJodaMoney(45.92),
        items = expectedBasketItems)

      resp.basket shouldBe expectedBasket
    }

    scenario("Apply an amount-off promotion") {
      Given("a basket request with amount-off voucher code")
      val basketAmountOff = BasketItemRequest(Some(TestAmountOffPromo.promoCode), addBasketItems)

      When("apply Amount-off voucher is invoked")
      val resp = Await.result(basketService.calculateBasket("someUser", basketAmountOff, products), 2 seconds)

      Then("amount-off promotion should be applied to the total price")
      val expectedBasket = Basket(addBasketItems.size,
        totalPrice = MoneyDom.asJodaMoney(43.38),
        totalVAT = MoneyDom.asJodaMoney(6.07),
        promo = Some(PromoSummary(MoneyDom.asJodaMoney(5), TestAmountOffPromo.promoCode, TestAmountOffPromo.description, TestAmountOffPromo.image)),
        deliveryCharge = MoneyDom.asJodaMoney(8.00),
        grandTotal = MoneyDom.asJodaMoney(52.45),
        items = expectedBasketItems)

      resp.basket shouldBe expectedBasket
    }

    scenario("Apply promo on deliveryCharge") {
      Given("a basket request with free-delivery voucher code")
      val basketFreeDelivery = BasketItemRequest(Some(TestPromoOnDelivery.promoCode), addBasketItems)

      When("apply free-delivery voucher is invoked")
      val resp = Await.result(basketService.calculateBasket("someUser", basketFreeDelivery, products), 2 seconds)

      Then("free-delivery promotion should be applied to the delivery charge")
      val expectedBasket = Basket(addBasketItems.size,
        totalPrice = MoneyDom.asJodaMoney(43.38),
        totalVAT = MoneyDom.asJodaMoney(5.05),
        promo = Some(PromoSummary(MoneyDom.asJodaMoney(8), TestPromoOnDelivery.promoCode, TestPromoOnDelivery.description, TestPromoOnDelivery.image)),
        deliveryCharge = MoneyDom.asJodaMoney(8.00),
        grandTotal = MoneyDom.asJodaMoney(48.43),
        items = expectedBasketItems)

      resp.basket shouldBe expectedBasket
    }

    scenario("calculate basket without promo") {
      Given("a basket request without voucher code")
      val basketAmountOff = BasketItemRequest(None, addBasketItems)

      When("basketService calculate basket is invoked")
      val resp = Await.result(basketService.calculateBasket("someUser", basketAmountOff, products), 2 seconds)

      Then("no promotion discount shoulbe applied")
      val expectedBasket = Basket(addBasketItems.size,
        totalPrice = MoneyDom.asJodaMoney(43.38),
        totalVAT = MoneyDom.asJodaMoney(6.65),
        promo = None,
        deliveryCharge = MoneyDom.asJodaMoney(8.00),
        grandTotal = MoneyDom.asJodaMoney(58.03),
        items = expectedBasketItems)

      resp.basket shouldBe expectedBasket
    }
  }

  feature("Apply  25% off promotion on first  order to basket") {

    scenario("apply 25% promo first order") {
      Given("a basket request with 25% off first promo")
      val orderRepository1 = mock[OrderRepository]
      val promoRepository1 = new PromoRepositoryImpl
      val promotionService = new PromotionServiceImpl(orderRepository1, promoRepository1)
      val basketService = new BasketServiceImpl(pricingService, promotionService, BasketConfig(Some(MoneyDom.asJodaMoney(25)), 0.5, "15:00", MoneyDom.asJodaMoney(8)))
      val basket = BasketItemRequest(Some(Welcome25.promoCode), addBasketItems)

      When("basketService calculate basket is invoked")
      mockitoWhen(orderRepository1.getUserOrders(mockMatchers.any[String])).thenReturn(Future.successful(Seq()))

      Then("basket should be calculated  with 25% off the total price")
      val expectedBasket = Basket(addBasketItems.size,
        totalPrice = MoneyDom.asJodaMoney(43.38),
        totalVAT = MoneyDom.asJodaMoney(5.39),
        promo = Some(PromoSummary(MoneyDom.asJodaMoney(10.85), Welcome25.promoCode, Welcome25.description, Welcome25.image)),
        deliveryCharge = MoneyDom.asJodaMoney(8.00),
        grandTotal = MoneyDom.asJodaMoney(45.92),
        items = expectedBasketItems)

      val resp = Await.result(basketService.calculateBasket("someUser", basket, products), 2 seconds)
      resp.basket shouldBe expectedBasket
    }


    scenario("apply 25% promo but user already redeemed promo before") {
      Given("a basket request with 25% off first promo")
      val orderRepository1 = mock[OrderRepository]
      val promoRepository1 = new PromoRepositoryImpl
      val promotionService = new PromotionServiceImpl(orderRepository1, promoRepository1)
      val basketService = new BasketServiceImpl(pricingService, promotionService,
        BasketConfig(Some(MoneyDom.asJodaMoney(25)), .5, "15:00", MoneyDom.asJodaMoney(8)))
      val basket = BasketItemRequest(Some(Welcome25.promoCode), addBasketItems)

      When("basketService calculate basket is invoked")
      mockitoWhen(orderRepository1.getUserOrders(mockMatchers.any[String])).thenReturn(Future.successful(Seq(purchasedOrder)))

      Then("basket should be calculated without any discount")
      val expectedBasket = Basket(addBasketItems.size,
        totalPrice = MoneyDom.asJodaMoney(43.38),
        totalVAT = MoneyDom.asJodaMoney(6.65),
        promo = None,
        deliveryCharge = MoneyDom.asJodaMoney(8.00),
        grandTotal = MoneyDom.asJodaMoney(58.03),
        items = expectedBasketItems)

      val resp = Await.result(basketService.calculateBasket("someUser", basket, products), 2 seconds)
      resp.basket shouldBe expectedBasket

      And("a service hint indicating user already redemeed promo should be returned")
      resp.hints shouldBe Some(List(ServiceHint(ServiceHint.promotion_already_redeemed_key, "voucher already redemmed")))
    }
  }
}

trait BasketServiceImplFixture extends MockitoSugar with Matchers {

  import DateSerializerUtil._

  implicit val ec = ExecutionContext.Implicits.global
  val injector = Guice.createInjector(new AppModule())

  val pricingService = mock[PricingService]
  val orderRepository = injector.instance[OrderRepository]
  val promoRepository = new DummyPromoRepository
  val promotionService = new PromotionServiceImpl(orderRepository, promoRepository)
  val basketService = new BasketServiceImpl(pricingService, promotionService, BasketConfig(Some(MoneyDom.asJodaMoney(5)), 0.5, "15:00", MoneyDom.asJodaMoney(8)))
  val VATable_RATE: Double = .2
  val NON_VATable_RATE: Double = 0

  def pricePlusVAT(price: Double, vAT_RATE: Double) = price * (1 + vAT_RATE)

  val prodId1 = "01"
  val prodId2 = "02"
  val prodId3 = "03"

  val addBasketItems: Set[AddBasketItem] =
    Set(AddBasketItem(prodId1, 2), AddBasketItem(prodId2, 2), AddBasketItem(prodId3, 2))

  val products = getProducts(addBasketItems).toSeq
  val expectedBasketItems = getBasketItems(addBasketItems, products).toList

  def getProducts(addItems: Set[AddBasketItem]): Set[ProductRep] =
    Set(ProductRep(
      id = prodId1,
      price = ProductPriceRep(value = 4.15, vatRate = VATable_RATE, valueIncludingVAT = pricePlusVAT(4.15, VATable_RATE)),
      discountedPrice = Some(ProductPriceRep(value = 3.73, vatRate = VATable_RATE, valueIncludingVAT = pricePlusVAT(3.73, VATable_RATE))),
      leadTime = 1,
      servings = UnitOfMeasurementRep("people", 5),
      productCode = Random.nextString(8),
      name = "Product " + prodId1,
      availability = true
    ),
      ProductRep(
        id = prodId2,
        price = ProductPriceRep(value = 9.50, vatRate = NON_VATable_RATE, valueIncludingVAT = pricePlusVAT(9.50, NON_VATable_RATE)),
        discountedPrice = None,
        leadTime = 1,
        servings = UnitOfMeasurementRep("people", 5),
        productCode = Random.nextString(8),
        name = "Product " + prodId2,
        availability = true
      ),
      ProductRep(
        id = prodId3,
        price = ProductPriceRep(value = 8.46, vatRate = VATable_RATE, valueIncludingVAT = pricePlusVAT(8.46, VATable_RATE)),
        discountedPrice = None,
        leadTime = 1,
        servings = UnitOfMeasurementRep("people", 5),
        productCode = Random.nextString(8),
        name = "Product " + prodId3,
        availability = true
      ))

  def getBasketItems(addItems: Set[AddBasketItem], products: Seq[ProductRep]): Set[BasketItem] = {
    val productMap = products.groupBy(_.id).mapValues(_.head)
    addItems map { item =>
      BasketItem(
        id = item.productId,
        quantity = item.quantity,
        productCode = productMap(item.productId).productCode,
        price = MoneyDom.asJodaMoney(productMap(item.productId).price.value * item.quantity),
        discountedPrice = productMap(item.productId).discountedPrice.map(d => MoneyDom.asJodaMoney(d.value * item.quantity)),
        unitPrice = MoneyDom.asJodaMoney(productMap(item.productId).price.value),
        name = productMap(item.productId).name,
        leadTime = productMap(item.productId).leadTime,
        vatRate = productMap(item.productId).price.vatRate
      )
    }
  }

  val basketItemList = List(
    BasketItem("68ae0d3f-34dd-41a0-b37e-1e78a2e49670", "Product " + Random.nextInt, "1e78a2e49670", 2, MoneyDom.asJodaMoney(37), None, MoneyDom.asJodaMoney(37),  1, 0.2),
    BasketItem("66dc188f-8304-4e0f-8161-428067af60ae", "Product " + Random.nextInt, "428067af60ae", 2, MoneyDom.asJodaMoney(37.9), None, MoneyDom.asJodaMoney(37.9), 1, 0.2),
    BasketItem("4fc0c0b5-1a2d-4b4d-8a2f-589c6b081afd", "Product " + Random.nextInt, "589c6b081afd", 2, MoneyDom.asJodaMoney(2.5), None, MoneyDom.asJodaMoney(2.5), 1, 0.0)
  )

  val promoSum = Some(PromoSummary(MoneyDom.asJodaMoney(18.85), TestPercentageOffPromo.promoCode, TestPercentageOffPromo.description, TestPercentageOffPromo.image))

  val basketDom = Basket(
    3,
    MoneyDom.asJodaMoney(75.4),
    MoneyDom.asJodaMoney(12.91),
    promoSum,
    MoneyDom.asJodaMoney(8),
    MoneyDom.asJodaMoney(77.46),
    basketItemList)

  val address = Address("5 cheapSide, officeserve", Some("cheapside, St pauls"), None, "EC2V 6AA", "London", Some("work"), Some("make a quick delivery"))

  val deliveryAddress = DeliveryAddress(None, "momo", address)

  val purchasedOrder = Order(
    "some Id",
    "someUser",
    basketDom,
    DateSerializerUtil.dateStringToZonedDateTime("2016-09-09T08:00:00+01:00"),
    Processed,
    None,
    None,
    Some(DateSerializerUtil.dateStringToZonedDateTime("2016-09-09T08:00:00+01:00")),
    Some(DeliverySlot("08:00", "10:00", "08:00 AM to 10:00 AM")),
    Some(deliveryAddress),
    None,
    Some("07664324677"),
    None,
    NoTableware(33))
}

object BasketServiceImplTest {

  class DummyPromoRepository extends PromoRepository {
    override val promotions: Set[Promotion] = Set(TestPercentageOffPromo, TestAmountOffPromo, TestPromoOnDelivery)
  }

  case object TestPercentageOffPromo extends Promotion {
    override val discountAmount: Double = 0.25
    override val promoCode = "WELCOME25"
    override val description = "25% first order discount off total"
    override val promoType = PercentageOffPromoType
    override val image: Link = Link("image", "some linnk", "image/jpg")

    override def getDiscount(totalPrice: MoneyDom, totalDelivery: MoneyDom): Discount = calculateDiscount(Some(totalPrice), None)
  }

  case object TestAmountOffPromo extends Promotion {
    override val discountAmount: Double = 5
    override val promoCode = "5POUNDOFF"
    override val description = "5 pound off total"
    override val promoType = AmountOffPromoType
    override val image: Link = Link("image", "some linnk", "image/jpg")

    override def getDiscount(totalPrice: MoneyDom, totalDelivery: MoneyDom): Discount = calculateDiscount(Some(totalPrice), None)

  }

  case object TestPromoOnDelivery extends Promotion {
    override val discountAmount: Double = 1
    override val promoCode = "FREEDELIVERY"
    override val description = "free delivery promo"
    override val promoType = PercentageOffPromoType
    override val image: Link = Link("image", "some linnk", "image/jpg")

    override def getDiscount(totalPrice: MoneyDom, totalDelivery: MoneyDom): Discount = calculateDiscount(None, Some(totalDelivery))
  }

}