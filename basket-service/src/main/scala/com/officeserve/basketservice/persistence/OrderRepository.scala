package com.officeserve.basketservice.persistence

import java.time.ZonedDateTime

import cats.data.Xor
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
import com.amazonaws.services.dynamodbv2.model._
import com.google.inject.ImplementedBy
import com.officeserve.basketservice.common.{DynamoDBSettings, DynamoDBSupport, DynamoDBTable}
import officeserve.commons.spray.auth.TrustedAuth.CognitoIdentityId
import com.gu.scanamo.error.DynamoReadError
import com.gu.scanamo.syntax._
import com.officeserve.basketservice.service.DynamoDBErrorException
import officeserve.commons.spray.webutils.DateSerializerUtil

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[OrderRepositoryImpl])
trait OrderRepository {
  def getOrders(orderIds: Seq[String]): Future[Seq[Order]]

  def getOrdersFor(status: OrderStatus, cutOffTime: ZonedDateTime): Future[Seq[Order]]


  def getUserOrders(userId: CognitoIdentityId): Future[Seq[Order]]

  def createOrder(order: Order): Future[String]

  def getOrder(id: String): Future[Option[Order]]

  def updateOrders(order: Order*): Future[Unit]

  def getInvoiceNumber(order: Order): Future[Int]
}

import com.officeserve.basketservice.persistence.OrderRepositoryImpl.globalSecondaryIndexes

class OrderRepositoryImpl(implicit settings: DynamoDBSettings, executor: ExecutionContext)
  extends DynamoDBSupport[Order](DynamoDBTable[Order]("orders",
    new ProvisionedThroughput(5l, 5l),
    'id -> S)()('invoiceNumber -> N, 'orderStatus -> S)(globalSecondaryIndexes)) with OrderRepository {

  val DefaultLimit = 1000

  override def createOrder(order: Order) = table.put(order).map(_ => ()).map(_ => order.id)

  implicit val ec = ExecutionContext.Implicits.global

  override def getOrder(id: String): Future[Option[Order]] = table.get(('id -> id))

  override def updateOrders(order: Order*): Future[Unit] = {
    table.putAll(order.toList).map(_ => Unit)
  }

  override def getInvoiceNumber(orderId: Order): Future[Int] = {
    Future.successful(233)
  }

  override def getUserOrders(userId: CognitoIdentityId): Future[Seq[Order]] =
    table.filter[Seq](Map("userId" -> new Condition().withComparisonOperator(ComparisonOperator.EQ)
      .withAttributeValueList(new AttributeValue().withS(userId))))

  override def getOrdersFor(status: OrderStatus,
                            cutOffTime: ZonedDateTime): Future[Seq[Order]] = {

    val orders = table.executeOperation(table.scanamoTable.index("orderStatus").query('orderStatus -> status))

    def applyFilter(o: Order): Boolean =
      o.orderStatus == status && o.cutOffTime.exists(d => d.toInstant.equals(cutOffTime.toInstant))

    orders.map { os: Seq[Xor[DynamoReadError, Order]] =>
      os.collect {
        case Xor.Right(o) => o
        case Xor.Left(error) => throw new DynamoDBErrorException(DynamoReadError.describe(error))
      }
    } map {
      _.filter(applyFilter)
    }

  }

  override def getOrders(orderIds: Seq[String]): Future[Seq[Order]] =
    table.executeOperation(table.scanamoTable.query('id -> orderIds.toSet)) map { os =>
      os.collect {
        case Xor.Right(o) => o
        case Xor.Left(error) => throw new DynamoDBErrorException(DynamoReadError.describe(error))
      }
    }
}

object OrderRepositoryImpl {
  val globalSecondaryIndexes =
    Set(new GlobalSecondaryIndex().withIndexName("invoiceNumber")
      .withKeySchema(new KeySchemaElement("invoiceNumber", KeyType.HASH))
      .withProvisionedThroughput(new ProvisionedThroughput(5l, 5l))
      .withProjection(new Projection().withProjectionType(ProjectionType.KEYS_ONLY)),
      new GlobalSecondaryIndex().withIndexName("orderStatus")
        .withKeySchema(new KeySchemaElement("orderStatus", KeyType.HASH))
        .withProvisionedThroughput(new ProvisionedThroughput(5l, 5l))
        .withProjection(new Projection().withProjectionType(ProjectionType.ALL)))
}
