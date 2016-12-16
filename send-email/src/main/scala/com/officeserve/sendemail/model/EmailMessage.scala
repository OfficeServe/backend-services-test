package com.officeserve.sendemail.model

import java.net.URL

case class EmailMessage(from: String,
                        to: Set[String],
                        cc: Set[String],
                        bcc: Set[String],
                        subject: String,
                        body: String,
                        attachments: Set[URL],
                        format: EmailFormat)

sealed trait EmailFormat

object Html extends EmailFormat

object Txt extends EmailFormat

object EmailFormat {
  def apply(str: String):EmailFormat = str.toLowerCase match {
    case "html" => Html
    case "txt" => Txt
    case _ => Txt
  }
}