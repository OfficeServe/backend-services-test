package com.officeserve.lambda

import com.amazonaws.services.lambda.runtime.events.SNSEvent
import com.amazonaws.services.lambda.runtime.events.SNSEvent.SNSRecord
import com.amazonaws.services.lambda.runtime.{Context, LambdaLogger}
import com.amazonaws.services.simpleemail.model.{SendRawEmailRequest, SendRawEmailResult}
import com.github.dwhjames.awswrap.ses.AmazonSimpleEmailServiceScalaClient
import com.officeserve.sendemail.lambda.SendEmailLambda
import com.officeserve.sendemail.lambda.settings.EmailSettings
import org.mockito.Mockito._
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.mockito.{Matchers, Mockito}
import org.scalatest.mock.MockitoSugar
import org.scalatest.{BeforeAndAfterEach, FeatureSpec, GivenWhenThen}

import scala.collection.JavaConverters._
import scala.concurrent.Future

class SendEmailLambdaTest extends FeatureSpec with GivenWhenThen with SendEmailLambda with MockitoSugar with BeforeAndAfterEach {

  import SendEmailLambdaTest._

  val sesClient = mock[AmazonSimpleEmailServiceScalaClient]
  override val settings = EmailSettings("from","local","Hello",Some(sesClient))

  val context = mock[Context]
  val logger = mock[LambdaLogger]

  Mockito.when(context.getLogger).thenReturn(logger)
  Mockito.when(logger.log(Matchers.anyString())).thenAnswer(new Answer[Unit] {
    override def answer(invocation: InvocationOnMock): Unit =
      println(invocation.getArguments.apply(0))
  })

  override def beforeEach() {
    reset(sesClient)
    Mockito.when(sesClient
      .sendRawEmail(
        Matchers.any[SendRawEmailRequest]()
      )
    ) thenReturn Future.successful(new SendRawEmailResult().withMessageId("my-message-id"))
  }

  scenario("A purchase complete event was published by Basket Service") {
    Given(s"A purchase complete event")
    val event = snsEvent(snsMessage)
    When(s"Lambda function is called")
    handleRequest(event,context)
    Then(s"It should send an email to the user who made the purchase")
    verify(sesClient).sendRawEmail(Matchers.any[SendRawEmailRequest])
  }

  scenario("A purchase failed event was published by Basket Service") {
    Given(s"A purchase failed event")
    val event = snsEvent(anotherSnsMessage)
    When(s"Lambda function is called")
    handleRequest(event,context)
    Then(s"It should NOT send an email to the user who made the purchase")
    verifyZeroInteractions(sesClient)
  }

  scenario("An unknown message was published to SNS") {
    Given(s"A purchase failed event")
    val event = snsEvent("Hello world!")
    When(s"Lambda function is called")
    handleRequest(event,context)
    Then(s"It should NOT send any emails")
    verifyZeroInteractions(sesClient)
  }
}


object SendEmailLambdaTest {

  val snsMessage =
    """{
       "eventType":"SendEmail",
        "entities": [{
          "from": "n.cavallo@officeserve.com",
          "to": ["n.cavallo@officeserve.com"],
          "subject": "A subject",
          "body": "A body",
          "format": "html"
        }]
       }
    """

  val anotherSnsMessage =
    """
      |{"eventType":"EVENT_PURCHASE_FAILED",
      |"message":{"order":{"id":"598e3993","userId":"1234","basket":{"itemsCount":2,"totalPrice":{"value":30.0,"currency":"GBP"},"totalVAT":{"value":10.0,"currency":"GBP"},"deliveryCharge":{"value":5.0,"currency":"GBP"},"grandTotal":{"value":5.0,"currency":"GBP"},"items":[{"productId":"abgadgdiajkbdak","quantity":2,"price":{"value":10.0,"currency":"GBP"}}]},"invoiceNumber":13,
      |"createdDate":"2016-08-04T11:21:51+01:00","deliveryDate":"2016-08-04T11:21:51+01:00","deliveryAddress":{"addressLine1":" 5 cheapSide, officeserver","addressLine2":"St paul's","postCode":"EC2V 6AA","city":"London"},"invoiceAddress":{"addressLine1":" 5 cheapSide, officeserver","addressLine2":"St paul's","postCode":"EC2V 6AA","city":"London"},"telephoneNum":"0774789987","orderStatus":{"status":"SUCCEEDED"},"transactionId":"K1RqAwBwJbEd9GtYoeYTrAR582r","gatewayTransactionId":"54","retainedToken":true,"cardType":"visa","cardNumRepresentation":"XXXX-XXXX-XXXX-1111"},
      |"user":{"id":"ahgaiu","name":"Joe","username":"joe@Hotmail.com","email":"joe@Hotmail.com"}}}
    """.stripMargin

  def snsEvent(message: String): SNSEvent = {
    val snsEvent = new SNSEvent
    val sns = new SNSEvent.SNS()
    sns.setMessage(message)
    val snsRecord = new SNSRecord()
    snsRecord.setSns(sns)
    snsEvent.setRecords(List(snsRecord).asJava)
    snsEvent
  }



}