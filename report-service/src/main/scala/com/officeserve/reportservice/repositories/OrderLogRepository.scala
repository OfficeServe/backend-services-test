package com.officeserve.reportservice.repositories

import java.time.LocalDate

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsyncClient
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
import com.amazonaws.services.dynamodbv2.model.{AttributeValue, ProvisionedThroughput}
import com.gu.scanamo.DynamoFormat
import com.gu.scanamo.error.{DynamoReadError, TypeCoercionError}
import com.gu.scanamo.syntax._
import com.officeserve.basketservice.web._
import com.officeserve.commons.dynamodb.{DynamoDBSettings, DynamoDBTable}
import com.officeserve.reportservice.models.orderlog.OrderLog
import com.officeserve.reportservice.repositories.OrderLogRepository._

import scala.collection.JavaConverters._
import scala.collection.immutable.Set
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}


trait OrderLogRepository {

  def put(orderLog: OrderLog): Future[Unit]

  def query(cutOffDate: LocalDate): Future[Seq[OrderLog]]

}


object OrderLogRepository {

  def apply(settings: DynamoDBSettings)(implicit ec: ExecutionContext) =
    new OrderLogRepositoryImpl(settings, new AmazonDynamoDBAsyncClient().withEndpoint[AmazonDynamoDBAsyncClient](settings.endpoint))

  def apply(settings: DynamoDBSettings, client: AmazonDynamoDBAsyncClient)(implicit ec: ExecutionContext) =
    new OrderLogRepositoryImpl(settings, client)


  private[repositories] implicit val basketItemRepFormat = DynamoFormat[BasketItemRep]

  private[repositories] implicit object setFormat extends DynamoFormat[Set[BasketItemRep]] {
    override def read(av: AttributeValue): Either[DynamoReadError, Set[BasketItemRep]] =
      Try(av.getL.asScala.toSet) match {
        case Success(s) =>
          val x: Set[Either[DynamoReadError, BasketItemRep]] = s.map(av => basketItemRepFormat.read(av))
          x.partition(_.isLeft) match {
            case (failed, succeeded) if failed.isEmpty =>
              val basketItemReps = for {
                Right(s) <- succeeded.view
              } yield s
              Right(basketItemReps.toSet)
            case (failed, _) =>
              val errors = for {
                Left(l) <- failed.view
              } yield l
              Left(errors.head)
          }
        case Failure(e) => Left(TypeCoercionError(e))
      }


    override def write(t: Set[BasketItemRep]): AttributeValue =
      new AttributeValue().withL(t.toList.map { basketItem =>
        basketItemRepFormat.write(basketItem)
      }.asJava)
  }

  private[repositories] implicit val eventLogFormat = DynamoFormat[OrderLog]

}


class OrderLogRepositoryImpl(settings: DynamoDBSettings, amazonDynamoDBAsyncClient: AmazonDynamoDBAsyncClient)(implicit ec: ExecutionContext) extends OrderLogRepository {


  private implicit val awsClient = amazonDynamoDBAsyncClient
  private implicit val awsSettings = settings

  private val table = DynamoDBTable[OrderLog]("ordersLog", new ProvisionedThroughput(5l, 5l), 'cutOffDate -> S, Some('eventId -> S))()()


  override def put(event: OrderLog): Future[Unit] =
    table.put(event)
      .map(_ => {})

  override def query(cutOffDate: LocalDate): Future[Seq[OrderLog]] = {
    failOnFirstError {
      table.query('cutOffDate -> cutOffDate.format(isoLocalDateFormatter))
    }
  }
}



