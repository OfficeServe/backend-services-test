package com.officeserve.basketservice.clients

import akka.actor.ActorSystem
import com.officeserve.basketservice.service.InvalidRequestException
import com.officeserve.basketservice.settings.CatalogueClientSettings
import officeserve.commons.spray.webutils.CommonJsonProtocol
import spray.http.{HttpRequest, HttpResponse, StatusCodes}

import scala.concurrent.{ExecutionContext, Future}
import spray.client.pipelining._
import spray.httpx.SprayJsonSupport
import spray.httpx.unmarshalling._
import spray.json.JsonFormat

trait CatalogueClient extends CatalogueClientJsonProtocol with SprayJsonSupport  {

  def getAvailableProducts(productIds: Seq[String])(implicit ec: ExecutionContext): Future[Seq[ProductRep]]
  def getProducts(productIds: Seq[String])(implicit ec: ExecutionContext): Future[Seq[ProductRep]]

}

object CatalogueClient {

  def apply(catalogueClientSettings:CatalogueClientSettings)(implicit system: ActorSystem):CatalogueClient =
    new CatalogueClientImpl(catalogueClientSettings)

}

private class CatalogueClientImpl(catalogueClientSettings:CatalogueClientSettings)(implicit system: ActorSystem) extends CatalogueClient {

  import StatusCodes._

  private def buildGetRequest(path: String, productIds: Seq[String]): HttpRequest =
    Get(s"${catalogueClientSettings.baseUrl}$path/${productIds.mkString(",")}")

  override def getAvailableProducts(productIds: Seq[String])
                                   (implicit ec: ExecutionContext): Future[Seq[ProductRep]] = {

    val result = getProducts(productIds)

    result map {
      case seq
        if seq.size != productIds.size =>
        throw new ProductNotFoundException(productIds.filterNot(seq.map(_.id).contains))
      case seq if !seq.exists(_.availability) =>
        throw new ProductUnavailableException(seq.filterNot(_.availability).map(_.id))
      case seq => seq
    }
  }

  override def getProducts(productIds: Seq[String])(implicit ec: ExecutionContext): Future[Seq[ProductRep]] = {
    if (productIds.isEmpty) {
      return Future.failed(new InvalidRequestException("Empty product list."))
    }

    val pipeline: HttpRequest => Future[HttpResponse] = sendReceive

    val responseFut: Future[HttpResponse] = pipeline(buildGetRequest("/products", productIds))

    for {
      r <- responseFut
    } yield {
      r match {
        case x if x.status == NotFound =>
          throw new ProductNotFoundException(productIds)
        case x if x.status.intValue >= BadRequest.intValue =>
          throw new BadGetProductsRequestException(productIds,x.entity.asString)
        case x =>
          x.as[Seq[ProductRep]]
            .fold[Seq[ProductRep]]({
            case MalformedContent(m, c) =>
              throw new IllegalStateException(m,c.get)
            case e =>
              throw new IllegalStateException("Deserialization error")
          }, identity)
      }
    }
  }

}

sealed abstract class CatalogueClientException(val productIds: Seq[String], message: String = "Unknown Error")
  extends Exception(s"$message ids: [${productIds.mkString(",")}]")

case class ProductNotFoundException(override val productIds: Seq[String])
  extends CatalogueClientException(productIds, "Product(s) not found:")

case class BadGetProductsRequestException(override val productIds: Seq[String], message: String)
  extends CatalogueClientException(productIds,message)

case class ProductUnavailableException(override val productIds: Seq[String])
  extends CatalogueClientException(productIds, "Product(s) unavailable: ")

trait CatalogueClientJsonProtocol extends CommonJsonProtocol {
  //Product representation
  implicit val productPriceRepFormat:JsonFormat[ProductPriceRep] = jsonFormat4(ProductPriceRep)
  implicit val unitOfMeasurementRepFormat:JsonFormat[UnitOfMeasurementRep] = jsonFormat2(UnitOfMeasurementRep)
  implicit val productRepFormat:JsonFormat[ProductRep] = jsonFormat8(ProductRep)
}

case class ProductPriceRep(
                            currency: String = "GBP",
                            value: Double,
                            valueIncludingVAT: Double,
                            vatRate: Double = 0
                          )

case class UnitOfMeasurementRep(
                                 unit: String,
                                 value: Double
                               )

case class LinkRep(
                    rel: String,
                    href: String,
                    `type`: String
                  )

case class ProductRep(
                       id: String,
                       name: String,
                       price: ProductPriceRep,
                       discountedPrice: Option[ProductPriceRep],
                       leadTime: Int,
                       servings: UnitOfMeasurementRep,
                       productCode: String,
                       availability: Boolean
                     )