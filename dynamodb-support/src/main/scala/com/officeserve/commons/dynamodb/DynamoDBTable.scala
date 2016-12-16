package com.officeserve.commons.dynamodb

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsyncClient
import com.amazonaws.services.dynamodbv2.model._
import com.gu.scanamo.error.DynamoReadError
import com.gu.scanamo.ops.ScanamoOps
import com.gu.scanamo.query.Query
import com.gu.scanamo.syntax._
import com.gu.scanamo.{DynamoFormat, ScanamoAsync, Table}

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}


case class DynamoDBTable[T: DynamoFormat](name: String,
                                          throughput: ProvisionedThroughput,
                                          hashKey: (Symbol, ScalarAttributeType),
                                          rangeKey: Option[(Symbol, ScalarAttributeType)] = None
                                         )(extraAttributes: (Symbol, ScalarAttributeType)*
                                         )(globalSecondaryIndexes: Set[GlobalSecondaryIndex] = Set())
                                         (implicit val settings: DynamoDBSettings, awsClient: AmazonDynamoDBAsyncClient, ec: ExecutionContext) {

  import DynamoDBTable._

  private val fullName = settings.tablePrefix + name
  private val rk = rangeKey.fold(Seq.empty[(Symbol, ScalarAttributeType)])(tuple => Seq(tuple))
  private val attributes = hashKey +: (rk ++ extraAttributes)

  private val client = awsClient.withEndpoint[AmazonDynamoDBAsyncClient](settings.endpoint)
  private val scanamoTable = Table[T](fullName)

  createIfNotExists()

  def createIfNotExists(): Unit = if (!exists()) create()

  def exists(): Boolean =
    client.listTables().getTableNames.asScala.contains(fullName)

  def create(): Unit = {
    val createTableRequest =
      new CreateTableRequest().withTableName(fullName)
        .withAttributeDefinitions(attributes.map(attributeDef(_)).asJava)
        .withKeySchema((new KeySchemaElement(hashKey._1.name, KeyType.HASH) +:
          rk.map(k => new KeySchemaElement(k._1.name, KeyType.RANGE))).asJava)
        .withProvisionedThroughput(throughput)

    client.createTable {
      Option(globalSecondaryIndexes).filterNot(_.isEmpty).fold(createTableRequest) { indexes =>
        createTableRequest.withGlobalSecondaryIndexes(indexes.asJavaCollection)
      }
    }

  }

  def put(x: T): Future[PutItemResult] = ScanamoAsync.exec(client)(scanamoTable.put(x))

  def putAll(x: Set[T]): Future[List[BatchWriteItemResult]] = ScanamoAsync.exec(client)(scanamoTable.putAll(x))

  def get(id: String): Future[Option[Either[DynamoReadError, T]]] =
    ScanamoAsync.exec(client)(scanamoTable.get(hashKey._1 -> id))


  def executeOperation[A](op: ScanamoOps[A]): Future[A] =
    ScanamoAsync.exec(client)(op)

  def query(q: Query[_]): Future[List[Either[DynamoReadError, T]]] =
    ScanamoAsync.exec(client)(scanamoTable.query(q))

  def queryWithLimit(q: Query[_], limit: Int): Future[List[Either[DynamoReadError, T]]] =
    ScanamoAsync.exec(client)(scanamoTable.limit(limit).query(q))


}

object DynamoDBTable {

  private def attributeDef(attribute: (Symbol, ScalarAttributeType)) = {
    new AttributeDefinition(attribute._1.name, attribute._2)
  }

}
