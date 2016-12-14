package com.officeserve.basketservice.common

import cats.data.Xor
import com.amazonaws.services.dynamodbv2.model._
import com.gu.scanamo.error.DynamoReadError
import com.gu.scanamo.ops.ScanamoOps
import com.gu.scanamo.query.UniqueKey
import com.gu.scanamo.syntax._
import com.gu.scanamo.{DynamoFormat, ScanamoAsync, ScanamoFree, Table}
import com.officeserve.basketservice.service.DynamoDBErrorException
import officeserve.commons.spray.webutils.Error

import scala.collection.JavaConverters._
import scala.collection.generic.CanBuildFrom
import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds
import scala.util.Left

case class DynamoDBTable[T: DynamoFormat](name: String,
                                          throughput: ProvisionedThroughput,
                                          hashKey: (Symbol, ScalarAttributeType)
                                         )(rangeKeys: (Symbol, ScalarAttributeType)*
                                         )(extraAttributes: (Symbol, ScalarAttributeType)*
                                         )(globalSecondaryIndexes: Set[GlobalSecondaryIndex] = Set())
                                         (implicit val settings: DynamoDBSettings, ec: ExecutionContext) {

  import DynamoDBTable._

  val fullName = settings.tablePrefix + name
  val attributes = hashKey +: (rangeKeys ++ extraAttributes)
  val DEFAULT_LIMIT = 100

  val client = settings.client
  val scanamoTable = Table[T](fullName)

  def createIfNotExists(): Unit = if (!exists()) create()

  def internalServerError(e: Throwable) = Left(Error.withLogger(500, Some("internal server error"), None, e))


  def exists(): Boolean =
    client.listTables().getTableNames.asScala.contains(fullName)

  def create(): Unit = {
    val createTableRequest =
      new CreateTableRequest().withTableName(fullName)
        .withAttributeDefinitions(attributes.map(attributeDef(_)).asJava)
        .withKeySchema((new KeySchemaElement(hashKey._1.name, KeyType.HASH) +:
          rangeKeys.map(k => new KeySchemaElement(k._1.name, KeyType.RANGE))).asJava)
        .withProvisionedThroughput(throughput)

    client createTable {
      Option(globalSecondaryIndexes).filterNot(_.isEmpty).fold(createTableRequest) { indexes =>
        createTableRequest.withGlobalSecondaryIndexes(indexes.asJavaCollection)
      }
    }

  }

  def put(x: T): Future[PutItemResult] = ScanamoAsync.exec(client)(scanamoTable.put(x))

  def putAll(x: List[T]): Future[List[BatchWriteItemResult]] = ScanamoAsync.exec(client)(scanamoTable.putAll(x))

  def get(key: UniqueKey[_]): Future[Option[T]] = ScanamoAsync.exec(client)(scanamoTable.get(key)).map(_.collect {
    case Xor.Right(ps) => ps
    case Xor.Left(error) => throw new DynamoDBErrorException(DynamoReadError.describe(error))
  })

  //  def filter(cond: Map[String, Condition], limit: Int): Future[Seq[T]] =
  //    ScanamoAsync.exec(client)(ScanamoOps.scan(new ScanRequest()
  //      .withTableName(fullName)
  //      .withScanFilter(cond.asJava)
  //      .withLimit(limit))) map { v =>
  //      v.getItems.asScala.map(i => ScanamoFree.read[T](i))
  //    } map {
  //      _.collect {
  //        case Xor.Right(ps) => ps
  //        case Xor.Left(error) => throw new DynamoDBErrorException(DynamoReadError.describe(error))
  //      }
  //    }

  def filter[Coll[_]](cond: Map[String, Condition], limit: Int)(implicit cbf: CanBuildFrom[Coll[T], T, Coll[T]]): Future[Coll[T]] = {
    val builder: mutable.Builder[T, Coll[T]] = cbf()

    (ScanamoAsync.exec(client)(ScanamoOps.scan(new ScanRequest()
      .withTableName(fullName)
      .withScanFilter(cond.asJava)
      .withLimit(limit))) map { v =>
      v.getItems.asScala.map(i => ScanamoFree.read[T](i))
    }) map { x =>
      x.foreach {
        case Xor.Right(ps) => builder += ps
        case Xor.Left(error) => throw new DynamoDBErrorException(DynamoReadError.describe(error))

      }
      builder.result()
    }
  }

  def executeOperation[A](op: ScanamoOps[A]) = ScanamoAsync.exec(client)(op)

  def delete(id: String): Future[DeleteItemResult] = ScanamoAsync.exec(client)(scanamoTable.delete('id -> id))

  def filters[Coll[_]](conditions: Set[FilterCondition], defaultLimit: Int = DEFAULT_LIMIT)(implicit cbf: CanBuildFrom[Coll[T], T, Coll[T]]): Future[Either[Error, Coll[T]]] = {
    filter(conditions.foldLeft(Map[String, Condition]())((a, c) => a ++ c.toCondition), defaultLimit)(cbf)
      .map(Right(_)) recover {
      case e => internalServerError(e)
    }
  }

}

object DynamoDBTable {

  private def attributeDef(attribute: (Symbol, ScalarAttributeType)) = {
    new AttributeDefinition(attribute._1.name, attribute._2)
  }

  def globalSecondaryIndexes(fieldNames: String*): Set[GlobalSecondaryIndex] = {
    fieldNames.map { f => new GlobalSecondaryIndex().withIndexName(f)
      .withKeySchema(new KeySchemaElement(f, KeyType.HASH))
      .withProvisionedThroughput(new ProvisionedThroughput(5l, 5l))
      .withProjection(new Projection().withProjectionType(ProjectionType.KEYS_ONLY))
    }.toSet
  }

}

case class FilterCondition(name: String, value: String, comparisonOperator: ComparisonOperator = ComparisonOperator.EQ) {
  def toCondition: Map[String, Condition] = Map(name -> new Condition().withComparisonOperator(comparisonOperator)
    .withAttributeValueList(new AttributeValue().withS(value)))
}
