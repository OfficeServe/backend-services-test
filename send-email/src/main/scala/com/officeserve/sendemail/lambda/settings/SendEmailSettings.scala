package com.officeserve.sendemail.lambda.settings

import com.amazonaws.regions.Regions
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceAsyncClient
import com.github.dwhjames.awswrap.ses.AmazonSimpleEmailServiceScalaClient
import com.typesafe.config.ConfigFactory

trait SendEmailSettings {
  val config = ConfigFactory.load()

  val settings: EmailSettings = EmailSettings(config.getString("email.sender.source"), config.getString("email.sender.region"), config.getString("email.subject"))
}

case class EmailSettings(source: String, region: String, subject: String, sesClientOpt: Option[AmazonSimpleEmailServiceScalaClient] = None) {

  val client = sesClientOpt.getOrElse(new AmazonSimpleEmailServiceScalaClient(
    new AmazonSimpleEmailServiceAsyncClient()
      .withRegion(Regions.fromName(region))))

}
