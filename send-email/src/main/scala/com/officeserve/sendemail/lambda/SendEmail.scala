package com.officeserve.sendemail.lambda

import com.amazonaws.services.lambda.runtime.events.SNSEvent
import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import com.amazonaws.services.s3.AmazonS3Client
import com.officeserve.sendemail.lambda.processors.MessageProcessor
import com.officeserve.sendemail.lambda.publishers.{EmailPublisher, Publisher}
import com.officeserve.sendemail.lambda.settings.{EmailSettings, SendEmailSettings}
import com.officeserve.sendemail.model.EmailMessage
import org.json4s._
import org.json4s.jackson.JsonMethods._

import scala.collection.JavaConverters._
import scala.language.{implicitConversions, postfixOps}
import scala.util.Try


class SendEmail extends SendEmailLambda with SendEmailSettings


trait SendEmailLambda extends RequestHandler[SNSEvent, Unit] {

  val settings:EmailSettings

  implicit val s3 = new AmazonS3Client()

  lazy val allHandlers:List[EmailProcessor] = List(new EmailProcessor(new EmailPublisher(settings)))

  override def handleRequest(event: SNSEvent, context: Context): Unit = {
    event.getRecords.asScala.foreach { e =>
      Try(parse(e.getSNS.getMessage)) map { snsMessage =>
        allHandlers.foreach { handler =>
          if (handler.isDefined(snsMessage)) {
            context.getLogger.log(s"Processing message: '${e.getSNS.getMessage.take(128)}...'")
            Try(handler.processMessage(snsMessage, context)) map { _ =>
              context.getLogger.log(s"Message sent successfully")
            } recover {
              case e1 => context.getLogger.log(s"[ERROR] ${e1.getMessage}")
            }
          }

        }
      } recover {
        case e1 => context.getLogger.log(s"[ERROR] Error parsing message: ${e1.getMessage}")
      }
    }
  }

}

class EmailProcessor(publisher: Publisher[EmailMessage]) extends MessageProcessor[EmailMessage,EmailMessage](publisher, "SendEmail") {

  override def doProcessMessage(inputMessage: EmailMessage): EmailMessage = inputMessage

}