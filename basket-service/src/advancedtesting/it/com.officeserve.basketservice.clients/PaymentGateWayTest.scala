package com.officeserve.basketservice.clients

import java.util.UUID

import akka.actor.ActorSystem
import cc.protea.spreedly.model.SpreedlyCreditCard
import com.officeserve.basketservice.persistence.{Failed, SpreedlyRequest, Succeeded}
import com.officeserve.basketservice.settings.BasketSettings
import com.ticketfly.spreedly.{SpreedlyClient, SpreedlyConfiguration}
import org.scalacheck.Prop.True
import officeserve.commons.domain.MoneyDom
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import org.scalatest.time.{Millis, Seconds, Span}


/**
  * Created by mo on 16/09/2016.
  */
class PaymentGateWayTest extends FlatSpec with Matchers with PaymentGateWayTestFixture with ScalaFutures {

  implicit val defaultPatience =
    PatienceConfig(timeout = Span(10, Seconds), interval = Span(500, Millis))

  val spreedlySettings = new BasketSettings().spreedlySettings
  val futureAwaitingTime = 10 seconds
  implicit val ec = ExecutionContext.Implicits.global
  implicit val system = ActorSystem("spreedlyClient-test")
  val config = SpreedlyConfiguration(spreedlySettings.environmentToken, spreedlySettings.accessSecret)
  val spreedlyClient = new SpreedlyClient(config)

  val paymemtGateway = new PaymentGatewayImpl(spreedlySettings)

  "paymentGateway" should " verify and retain a card successfully" in {

    val spreedLyRes = spreedlyClient.createPaymentMethod(validCreditCard)

    whenReady(spreedLyRes) { res =>
      paymemtGateway.verifyAndRetain(res.token, true).map { resp =>
        resp.isRight shouldBe true
        resp.right.map { resp =>
          resp.paymentStatus shouldBe Succeeded
          resp.retainedToken shouldBe true
          resp.truncatedCardRep shouldBe "XXXX-XXXX-XXXX-4444"
          resp.cardType shouldBe "MASTERCARD"
        }
      }
    }
  }

  "paymentGateway" should "successfully pay through spreedly - retain token" in {
    val spreedLyRes = spreedlyClient.createPaymentMethod(validCreditCard)

    whenReady(spreedLyRes) { spreedLyResponse =>
      val request = SpreedlyRequest(orderId, amount, currency, spreedLyResponse.token, true)
      paymemtGateway.pay(request).map { resp =>
        resp.paymentStatus shouldBe Succeeded
        resp.retainedToken shouldBe true
        resp.truncatedCardRep shouldBe "XXXX-XXXX-XXXX-4444"
        resp.cardType shouldBe "MASTERCARD"
      }
    }
  }

  "paymentGateway" should "successfully pay through spreedly - don't retain token" in {
    val spreedLyRes = spreedlyClient.createPaymentMethod(validCreditCard)

    whenReady(spreedLyRes) { spreedLyResponse =>
      val request = SpreedlyRequest(orderId, amount, currency, spreedLyResponse.token, false)
      paymemtGateway.pay(request).map { resp =>
        resp.paymentStatus shouldBe Succeeded
        resp.retainedToken shouldBe false
        resp.truncatedCardRep shouldBe "XXXX-XXXX-XXXX-4444"
        resp.cardType shouldBe "MASTERCARD"
      }
    }
  }

  "paymentGateway" should "fail payment with a declined card" in {
    val spreedLyRes = spreedlyClient.createPaymentMethod(invalidCreditCard)

    whenReady(spreedLyRes) { spreedLyResponse =>
      val request = SpreedlyRequest(orderId, amount, currency, spreedLyResponse.token, true)
      paymemtGateway.pay(request).map { resp =>
        resp.paymentStatus shouldBe Failed("GATEWAY_PROCESSING_FAILED: Unable to process the purchase transaction.")
        resp.retainedToken shouldBe false
        resp.truncatedCardRep shouldBe "XXXX-XXXX-XXXX-5100"
        resp.cardType shouldBe "MASTERCARD"
        resp.gatewayTransactionId shouldBe None
        resp.transactionId shouldNot be("")

      }
    }
  }

  "paymentGateway" should "refund a given transaction" in {
    val paymentMethod = Await.result(spreedlyClient.createPaymentMethod(validCreditCard), futureAwaitingTime).paymentMethod.token

    val request = SpreedlyRequest(orderId, amount, currency, paymentMethod, false)

    val resp = Await.result(paymemtGateway.pay(request), futureAwaitingTime)

    resp.paymentStatus shouldBe Succeeded
    resp.retainedToken shouldBe false
    resp.truncatedCardRep shouldBe "XXXX-XXXX-XXXX-4444"
    resp.cardType shouldBe "MASTERCARD"

    val cancellation = paymemtGateway
      .refund(resp.transactionId, MoneyDom.asJodaMoney(amount, currency))
      .futureValue
    cancellation.paymentStatus shouldBe Succeeded

  }

}

trait PaymentGateWayTestFixture {
  val validCreditCard = new SpreedlyCreditCard()
  validCreditCard.setFirstName("momo")
  validCreditCard.setLastName("OFFICESERVE")
  validCreditCard.setNumber("5555555555554444")
  validCreditCard.setVerificationValue("423")
  validCreditCard.setYear(2032)
  validCreditCard.setMonth(3)

  val invalidCreditCard = new SpreedlyCreditCard()
  invalidCreditCard.setFirstName("momo")
  invalidCreditCard.setLastName("OFFICESERVE")
  invalidCreditCard.setNumber("5105105105105100")
  invalidCreditCard.setVerificationValue("423")
  invalidCreditCard.setYear(2032)
  invalidCreditCard.setMonth(3)

  val orderId = UUID.randomUUID().toString

  val amount = 300

  val currency = "GBP"
}
