package com.officeserve.reportservice.repositories

import java.time.LocalDate

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsyncClient
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
import com.gu.scanamo.DynamoFormat
import com.officeserve.commons.dynamodb.{DynamoDBSettings, DynamoDBTable}
import com.officeserve.reportservice.models.productreport._
import com.gu.scanamo.syntax._

import scala.concurrent.{ExecutionContext, Future}

trait ProductReportLogRepository {
  def retrieveLatest(cutOffDate: LocalDate): Future[Option[ProductReport]]

  def put(productReportLog: ProductReport): Future[Unit]
}

object ProductReportLogRepository {

  def apply(settings: DynamoDBSettings)(implicit ec: ExecutionContext) =
    new ProductReportLogRepositoryImpl(settings, new AmazonDynamoDBAsyncClient().withEndpoint[AmazonDynamoDBAsyncClient](settings.endpoint))

  def apply(settings: DynamoDBSettings, client: AmazonDynamoDBAsyncClient)(implicit ec: ExecutionContext) =
    new ProductReportLogRepositoryImpl(settings, client)


  private[repositories] implicit val productReportEntryFormat = DynamoFormat[ProductReportEntry]
  private[repositories] implicit val productReportLogFormat = DynamoFormat[ProductReport]
}

class ProductReportLogRepositoryImpl(settings: DynamoDBSettings, amazonDynamoDBAsyncClient: AmazonDynamoDBAsyncClient)(implicit ec: ExecutionContext) extends ProductReportLogRepository {

  import ProductReportLogRepository._

  private implicit val awsClient = amazonDynamoDBAsyncClient
  private implicit val awsSettings = settings

  private val table = DynamoDBTable[ProductReport]("productReportLog", new ProvisionedThroughput(5l, 5l), 'cutOffDate -> S, Some('generatedOn -> S))()()

  override def retrieveLatest(cutOffDate: LocalDate): Future[Option[ProductReport]] =
    failOnFirstError {
      table.queryWithLimit(('cutOffDate -> cutOffDate.format(isoLocalDateFormatter)).descending, 1)
    }.map {
      _.headOption
    }

  override def put(productReportLog: ProductReport): Future[Unit] =
    table.put(productReportLog)
      .map(_ => {})
}