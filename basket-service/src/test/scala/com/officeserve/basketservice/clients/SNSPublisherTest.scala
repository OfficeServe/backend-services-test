package com.officeserve.basketservice.clients

import java.util.UUID

import com.amazonaws.services.sns.model.{NotFoundException, PublishResult}
import com.github.dwhjames.awswrap.sns.AmazonSNSScalaClient
import org.json4s.DefaultFormats
import org.mockito.{Mockito, Matchers => MockitoMatchers}
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FeatureSpec, GivenWhenThen, Matchers}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

class SNSPublisherTest extends FeatureSpec with GivenWhenThen with MockitoSugar with Matchers {

  val snsClient: AmazonSNSScalaClient = mock[AmazonSNSScalaClient]
  val sNSSender: SNSPublisher = new SNSPublisher(snsClient)
  implicit val formats = DefaultFormats
  val validTopic = "validTopic"
  val invalidTopic = "invalidTopic"

  Mockito.when(snsClient
    .publish(MockitoMatchers.eq(validTopic)
      ,MockitoMatchers.anyString()))
    .thenReturn(Future.successful(new PublishResult().withMessageId(UUID.randomUUID().toString)))

  Mockito.when(snsClient
    .publish(MockitoMatchers.eq(invalidTopic)
      ,MockitoMatchers.anyString()))
    .thenReturn(Future.failed(new NotFoundException(s"$invalidTopic not found")))

  scenario("Topic exists and message is correct") {
    Given(s"Topic is $validTopic")
    val message = EventMessage(PurchaseComplete, "this is my message")
    And(s"Message is $message")
    When("publish is called")
    val response = Await.result(sNSSender.publish(validTopic, message),1 second)
    Then("It should return a Disjunction with the right side populated")
    response.isRight shouldBe true
  }

  scenario("Topic does not exists and message is correct") {
    Given(s"Topic is $invalidTopic")
    val message = EventMessage(PurchaseCancelled, "this is my message")
    And(s"Message is $message")
    When("publish is called")
    val response = Await.result(sNSSender.publish(invalidTopic, message),1 second)
    Then("It should return a Disjunction with the left side populated")
    response.isLeft shouldBe true
  }

}
