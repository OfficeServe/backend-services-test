package com.officeserve.basketservice.persistence


import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
import com.amazonaws.services.dynamodbv2.model._
import com.google.inject.{ImplementedBy, Inject}
import com.officeserve.basketservice.common._
import officeserve.commons.spray.webutils.Error
import spray.http.StatusCodes.NotFound
import com.gu.scanamo.syntax._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Left
import DynamoDBTable._

/**
  * Created by mo on 23/11/2016.
  */
@ImplementedBy(classOf[PostCodeRepositoryImpl])
trait PostCodeRepository {
  def savePostCodes(postCodes: Set[PostCode]): Future[Either[Error, Unit]]

  def getPostcodeArea(area: String): Future[Either[Error, PostCode]]

  def getCoveredPostcodes: Future[Either[Error, Set[PostCode]]]

}


class PostCodeRepositoryImpl @Inject()(implicit settings: DynamoDBSettings, executor: ExecutionContext)
  extends DynamoDBSupport[PostCode](DynamoDBTable[PostCode]("postcodes",
    new ProvisionedThroughput(5l, 5l),
    'area -> S)()('coverageStatus -> S)(globalSecondaryIndexes("coverageStatus"))) with PostCodeRepository {

  val DefaultLimit = 500

  def internalServerError(e: Throwable) = Left(Error.withLogger(500, Some("internal server error"), None, e))

  override def savePostCodes(postCodes: Set[PostCode]): Future[Either[Error, Unit]] =
    table.putAll(postCodes.toList).map(_ => Right(())) recover {
      case e => Left(Error.withLogger(500, Some("internal server error"), None, e))
    }

  override def getPostcodeArea(area: String): Future[Either[Error, PostCode]] =
    table.get('area -> area).map(res => res.map(p => Right(p)).getOrElse(Left(Error(NotFound.intValue, Some("postCodeArea_not_found"), None)))) recover {
      case e => internalServerError(e)
    }

  override def getCoveredPostcodes: Future[Either[Error, Set[PostCode]]] =
    table.filters[Set](Set(FilterCondition("coverageStatus", "Covered", ComparisonOperator.EQ)))
}
