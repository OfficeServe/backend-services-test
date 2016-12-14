package com.officeserve.documentservice.api

import java.net.URL

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.Location
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import com.officeserve.documentservice.models.{Order, ProductReport}
import com.typesafe.config.{Config, ConfigFactory}
import org.json4s._
import org.json4s.jackson.Serialization
import org.json4s.jackson.Serialization.write

import scala.collection.immutable.Seq
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

trait DocumentServiceApi extends AutoCloseable {
  def generateInvoicePdf(order: Order): Future[URL]

  def generateReceiptPdf(order: Order): Future[URL]

  def generateInvoiceEmail(order: Order): Future[String]

  def generateReceiptEmail(order: Order): Future[String]

  def generateCancellationEmail(order: Order): Future[String]

  def generateDeliveryNotePdf(order: Order): Future[URL]

  def generateDeliveryNoteEmail(): Future[String]

  def generateDeliveryManifestPdf(orders: Seq[Order]): Future[URL]

  def generateProductReportPdf(productReport: ProductReport): Future[URL]

  def generateProductReportEmail(): Future[String]

}

class DocumentServiceApiImpl(config: Config = ConfigFactory.load())(implicit ec: ExecutionContext) extends DocumentServiceApi {

  import DocumentServiceApi._

  private implicit val system = ActorSystem("document-service-api")
  private implicit val materializer = ActorMaterializer()

  private val http = Http()
  private val baseUrl = config.getString("document-service-api.baseUrl")

  implicit private lazy val formats = Serialization.formats(NoTypeHints)

  override def generateInvoicePdf(invoice: Order): Future[URL] =
    generatePdf(s"$baseUrl/documents/invoice/pdf", Some(invoice))

  override def generateReceiptPdf(invoice: Order): Future[URL] =
    generatePdf(s"$baseUrl/documents/receipt/pdf", Some(invoice))

  override def generateDeliveryNotePdf(invoice: Order): Future[URL] =
    generatePdf(s"$baseUrl/documents/delivery-note/pdf", Some(invoice))

  override def generateDeliveryManifestPdf(orders: Seq[Order]): Future[URL] =
    generatePdf(s"$baseUrl/documents/delivery-manifest/pdf", Some(orders))

  override def generateProductReportPdf(productReport: ProductReport): Future[URL] =
    generatePdf(s"$baseUrl/documents/product-report/pdf", Some(productReport))



  override def generateInvoiceEmail(invoice: Order): Future[String] =
    generateHtml(s"$baseUrl/documents/invoice/email", Some(invoice))

  override def generateReceiptEmail(invoice: Order): Future[String] =
    generateHtml(s"$baseUrl/documents/receipt/email", Some(invoice))

  override def generateCancellationEmail(invoice: Order): Future[String] =
    generateHtml(s"$baseUrl/documents/cancellation/email", Some(invoice))

  override def generateDeliveryNoteEmail(): Future[String] =
    generateHtml(s"$baseUrl/documents/delivery-note/email")

  override def generateProductReportEmail(): Future[String] =
    generateHtml(s"$baseUrl/documents/product-report/email")



  private[this] def generatePdf[T <: AnyRef](endpoint: String, entity: Option[T] = None)(implicit formats: Formats): Future[URL] = {

    val request = HttpRequest(
      method = HttpMethods.POST,
      uri = endpoint,
      entity = HttpEntity(MediaTypes.`application/json`, write(entity))
    )

    http.singleRequest(request).flatMap {
      case FindLocation(header) =>
        Try(new URL(header.value())) match {
          case Success(url) => Future.successful(url)
          case f@Failure(e) => Future.failed(e)
        }
      case response => Future.failed(HeaderNotFoundException(response))
    }
  }

  private[this] def generateHtml[T <: AnyRef](endpoint: String, entity: Option[T] = None)(implicit formats: Formats): Future[String] = {
    val request = HttpRequest(
      method = HttpMethods.POST,
      uri = endpoint,
      entity = HttpEntity(MediaTypes.`application/json`, write(entity))
    )

    http.singleRequest(request).flatMap {
      case HttpResponse(StatusCodes.Created, _, httpEntity, _) =>
        httpEntity.dataBytes
          .map(_.decodeString(HttpCharsets.`UTF-8`.nioCharset()))
          .runWith(Sink.reduce[String]((acc, curr) => acc + curr))
      case response => Future.failed(WrongServerResponse(response))
    }

  }

  override def close(): Unit = {
    materializer.shutdown()
    system.terminate()
  }

}

object DocumentServiceApi {
  def apply(config: Config = ConfigFactory.load())(implicit ec: ExecutionContext) = {
    new DocumentServiceApiImpl(config)
  }

  object FindLocation {
    def unapply(arg: HttpResponse): Option[HttpHeader] = {
      arg match {
        case HttpResponse(StatusCodes.Created, headers, _, _) =>
          val index = headers.indexWhere(_.is(Location.lowercaseName))
          if (index >= 0) {
            Some(headers(index))
          } else {
            None
          }
        case _ => None
      }

    }
  }

  sealed trait DocumentServiceApiException

  case class HeaderNotFoundException(response: HttpResponse) extends Exception(s"Header not found in HTTP response:\n\n$response")

  case class WrongServerResponse(response: HttpResponse) extends Exception(s"Wrong HTTP response:\n\n$response")

}