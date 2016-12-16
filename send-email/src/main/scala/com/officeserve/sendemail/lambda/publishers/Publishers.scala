package com.officeserve.sendemail.lambda.publishers

import java.io.{ByteArrayOutputStream, InputStream}
import java.net.URL
import java.nio.ByteBuffer
import java.util.Properties
import javax.activation.DataHandler
import javax.mail.Session
import javax.mail.internet.{InternetAddress, MimeBodyPart, MimeMessage, MimeMultipart}
import javax.mail.util.ByteArrayDataSource

import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.simpleemail.model.{Body, _}
import com.amazonaws.util.IOUtils
import com.officeserve.sendemail.lambda.settings.EmailSettings
import com.officeserve.sendemail.lambda.utils.FutureUtils
import com.officeserve.sendemail.model._

import scala.collection.JavaConverters._

import scala.language.implicitConversions


trait Publisher[T] {
  def publish(o: T): Unit
}

class EmailPublisher(settings: EmailSettings)(implicit val s3: AmazonS3Client)
  extends Publisher[EmailMessage] with EmailFormatter with FutureUtils {

  override def publish(emailMessage: EmailMessage): Unit =
    sendEmail(emailMessage)

  private def sendEmail(emailMessage: EmailMessage): Unit = {
    implicit val emailFormat = emailMessage.format
    val _: SendRawEmailResult = settings.client.sendRawEmail(emailMessage)

  }

}

trait EmailFormatter {

  def extractBucketName(att: URL): String =
    att.getPath.tail.takeWhile(_ != '/')


  def extractFileName(att: URL): String =
    att.getPath.tail.dropWhile(_ != '/').tail

  implicit def toSendRawMessageRequest(message: EmailMessage)
                                      (implicit s3: AmazonS3Client,
                                       emailFormat: EmailFormat): SendRawEmailRequest = {

    val session = Session.getInstance(new Properties(), null)
    val mimeMessage = new MimeMessage(session)

    mimeMessage.setFrom(new InternetAddress(message.from))
    message.to foreach { toMail =>
      mimeMessage.addRecipient(javax.mail.Message.RecipientType.TO, new InternetAddress(toMail))
    }

    message.cc foreach { toMail =>
      mimeMessage.addRecipient(javax.mail.Message.RecipientType.CC, new InternetAddress(toMail))
    }

    message.bcc foreach { toMail =>
      mimeMessage.addRecipient(javax.mail.Message.RecipientType.BCC, new InternetAddress(toMail))
    }

    // Subject
    mimeMessage.setSubject(message.subject)

    // Add a MIME part to the message
    val mimeBodyPart = new MimeMultipart()
    val bodyPart = new MimeBodyPart()
    bodyPart.setText(message.body, "UTF-8", "html")
    mimeBodyPart.addBodyPart(bodyPart)

    // Add a attachement to the message
    message.attachments foreach { att =>

      val bucketName = extractBucketName(att)

      val fileName = extractFileName(att)

      val s3Object = s3.getObject(bucketName, fileName)

      val in: InputStream = s3Object.getObjectContent

      val bytes = IOUtils.toByteArray(in)

      val part = new MimeBodyPart()
      val source = new ByteArrayDataSource(bytes, s3Object.getObjectMetadata.getContentType)
      part.setDataHandler(new DataHandler(source))
      part.setFileName(fileName.reverse.takeWhile(_ != '/').reverse)
      mimeBodyPart.addBodyPart(part)
      in.close()

    }

    mimeMessage.setContent(mimeBodyPart)

    // Create Raw message
    val outputStream = new ByteArrayOutputStream()
    mimeMessage.writeTo(outputStream)
    val rawMessage = new RawMessage(ByteBuffer.wrap(outputStream.toByteArray))

    // Send Mail
    new SendRawEmailRequest(rawMessage)
      .withDestinations((message.to ++ message.cc ++ message.bcc).asJavaCollection)
      .withSource(message.from)
  }

}