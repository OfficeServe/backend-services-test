package com.officeserve.sendemail.lambda

import java.net.URL

import com.amazonaws.services.s3.AmazonS3Client
import com.officeserve.sendemail.lambda.publishers.EmailPublisher
import com.officeserve.sendemail.lambda.settings.EmailSettings
import com.officeserve.sendemail.model.{EmailMessage, Html}

object JustRun extends App {

  val emailSettings = EmailSettings("support@officeserve.com", "eu-west-1", "This is a test")

  implicit val s3 = new AmazonS3Client()

  val publisher = new EmailPublisher(emailSettings)
  publisher.publish(EmailMessage(emailSettings.source, Set("n.cavallo@officeserve.com"), Set(), Set(), emailSettings.subject, "Hi!",
    Set(new URL("https://s3-eu-west-1.amazonaws.com/testing-document/2016/11/03/KYM-9251234-invoice.pdf")), Html))
  sys.exit()

}
