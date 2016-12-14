package com.officeserve.documentservice.routes

import akka.http.scaladsl.model.HttpEntity.ChunkStreamPart
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{RequestContext, Route, RouteResult}
import akka.stream.scaladsl.StreamConverters
import com.officeserve.documentservice.marshallers.JsonSupport
import com.officeserve.documentservice.models._
import com.officeserve.documentservice.services.DocumentService

import scala.collection.immutable.Seq
import scala.concurrent.{ExecutionContext, Future}


class DocumentRoute(documentService: => DocumentService)(implicit val ec: ExecutionContext) extends Route with JsonSupport {
  override def apply(rc: RequestContext): Future[RouteResult] = {
    pathPrefix("documents") {
      pathPrefix("invoice") {
        path("pdf") {
          post {
            entity(as[Order]) { order =>
              onSuccess(documentService.generateInvoicePdf(order)) { url =>
                val location = Location(Uri(url.toURI.toString))
                complete(StatusCodes.Created, Seq(location))
              }
            }
          }
        } ~
          path("email") {
            post {
              entity(as[Order]) { order =>
                onSuccess(documentService.generateInvoiceEmail(order)) { htmlInputStream =>
                  val source = StreamConverters.fromInputStream(() => htmlInputStream)
                    .map(byteString => ChunkStreamPart(byteString))

                  complete(StatusCodes.Created, HttpEntity.Chunked(ContentTypes.`text/html(UTF-8)`, source))

                }
              }
            }
          }
      } ~
        pathPrefix("receipt") {
          path("pdf") {
            post {
              entity(as[Order]) { order =>
                onSuccess(documentService.generateReceiptPdf(order)) { url =>
                  val location = Location(Uri(url.toURI.toString))
                  complete(StatusCodes.Created, Seq(location))
                }
              }
            }
          } ~
            path("email") {
              post {
                entity(as[Order]) { order =>
                  onSuccess(documentService.generateReceiptEmail(order)) { htmlInputStream =>
                    val source = StreamConverters.fromInputStream(() => htmlInputStream)
                      .map(byteString => ChunkStreamPart(byteString))

                    complete(StatusCodes.Created, HttpEntity.Chunked(ContentTypes.`text/html(UTF-8)`, source))

                  }
                }
              }
            }
        } ~
        pathPrefix("cancellation") {
          path("email") {
            post {
              entity(as[Order]) { order =>
                onSuccess(documentService.generateCancellationEmail(order)) { htmlInputStream =>
                  val source = StreamConverters.fromInputStream(() => htmlInputStream)
                    .map(byteString => ChunkStreamPart(byteString))

                  complete(StatusCodes.Created, HttpEntity.Chunked(ContentTypes.`text/html(UTF-8)`, source))

                }
              }
            }
          }
        } ~
        pathPrefix("delivery-note") {
          path("pdf") {
            post {
              entity(as[Order]) { order =>
                onSuccess(documentService.generateDeliveryNotePdf(order)) { url =>
                  val location = Location(Uri(url.toURI.toString))
                  complete(StatusCodes.Created, Seq(location))
                }
              }
            }
          } ~
            path("email") {
              post {
                onSuccess(documentService.generateDeliveryNoteEmail) { htmlInputStream =>
                  val source = StreamConverters.fromInputStream(() => htmlInputStream)
                    .map(byteString => ChunkStreamPart(byteString))

                  complete(StatusCodes.Created, HttpEntity.Chunked(ContentTypes.`text/html(UTF-8)`, source))

                }
              }
            }
        } ~
        pathPrefix("delivery-manifest") {
          path("pdf") {
            post {
              entity(as[Seq[Order]]) { orders =>
                onSuccess(documentService.generateDeliveryManifestPdf(orders)) { url =>
                  val location = Location(Uri(url.toURI.toString))
                  complete(StatusCodes.Created, Seq(location))
                }
              }
            }
          }
        } ~
        pathPrefix("product-report") {
          path("pdf") {
            post {
              entity(as[ProductReport]) { productReport =>
                onSuccess(documentService.generateProductReportPdf(productReport)) { url =>
                  val location = Location(Uri(url.toURI.toString))
                  complete(StatusCodes.Created, Seq(location))
                }
              }
            }
          } ~
            path("email") {
              post {
                onSuccess(documentService.generateProductReportEmail) { htmlInputStream =>
                  val source = StreamConverters.fromInputStream(() => htmlInputStream)
                    .map(byteString => ChunkStreamPart(byteString))

                  complete(StatusCodes.Created, HttpEntity.Chunked(ContentTypes.`text/html(UTF-8)`, source))

                }
              }
            }
        }
    }.apply(rc)
  }
}
