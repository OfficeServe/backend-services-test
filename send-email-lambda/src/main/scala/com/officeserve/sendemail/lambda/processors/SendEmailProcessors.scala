package com.officeserve.sendemail.lambda.processors

import java.net.URL
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import com.amazonaws.services.lambda.runtime.Context
import com.officeserve.sendemail.lambda.publishers.Publisher
import com.officeserve.sendemail.model.EmailFormat
import org.json4s.{Formats, _}


abstract class MessageProcessor[T, P](publisher: Publisher[P],
                                      eventType: EventType*)
                                     (implicit m: scala.reflect.Manifest[T]) extends EventMessageProcessor {

  def isDefined(jsonMessage: JValue): Boolean =
    getEventType(jsonMessage).exists(eventType.contains)

  def doProcessMessage(inputMessage: T): P

  final def processMessage(jsonMessage: JValue, context: Context): Unit = {
    val purchaseCompleteMessages = extractEventMessage[Set[T]](jsonMessage)
    purchaseCompleteMessages foreach { purchaseCompleteMessage =>
      val emailMessage = doProcessMessage(purchaseCompleteMessage)
      publisher.publish(emailMessage)
    }
  }

}

trait EventMessageProcessor extends JsonCommonFormats {

  def getEventType(jsonMessage: JValue): Option[String] =
    (jsonMessage \ "eventType").extractOpt[String]

  def extractEventMessage[T](snsMessage: JValue)(implicit m: scala.reflect.Manifest[T]): T =
    (snsMessage \ "entities").extract[T]

}

trait JsonCommonFormats {

  case object EmailFormatSerializer extends CustomSerializer[EmailFormat](format => ( {
    case JString(str) => EmailFormat(str)
  }, {
    case ef: EmailFormat => JString(ef.getClass.getSimpleName.replaceAll("\\$",""))
  }))

  case object UrlFormatSerializer extends CustomSerializer[URL](format => ( {
    case JString(str) => new URL(str)
    case JNull => null
  }, {
    case url: URL => JString(url.toString)
  }))

  case object ZonedDateTimeSerializer extends CustomSerializer[ZonedDateTime](format => ( {
    case JString(strDate) => ZonedDateTime.parse(strDate, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
  }, {
    case zdt: ZonedDateTime => JString(zdt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
  }))

  implicit val formats: Formats = DefaultFormats + ZonedDateTimeSerializer + EmailFormatSerializer + UrlFormatSerializer

}


