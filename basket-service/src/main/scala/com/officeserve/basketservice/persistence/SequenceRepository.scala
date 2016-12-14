package com.officeserve.basketservice.persistence

import com.amazonaws.services.dynamodbv2.model.{ProjectionType, _}
import com.officeserve.basketservice.common.{DynamoDBSettings, DynamoDBSupport, DynamoDBTable}
import com.typesafe.scalalogging.StrictLogging
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
import com.amazonaws.services.dynamodbv2.model._
import scala.collection.JavaConverters._
import scala.concurrent.blocking

import scala.concurrent.{ExecutionContext, Future}

trait SequenceRepository {

  def createIfNotExists(sequence: Sequence): Unit


  def nextValueFor(sequenceName: String): Future[Long]

  def saveOrUpdate(sequence: Sequence): Future[Unit]

}

class SequenceRepositoryImpl(implicit settings: DynamoDBSettings, executor: ExecutionContext)
  extends DynamoDBSupport[Sequence](DynamoDBTable[Sequence]("sequences",
    new ProvisionedThroughput(5l, 5l),
    'id -> S)()()())
    with SequenceRepository
    with StrictLogging {

  def createUpdateRequest(s: Sequence): UpdateItemRequest = {
    new UpdateItemRequest().withTableName(table.fullName)
      .withReturnValues(ReturnValue.ALL_NEW)
      .addKeyEntry("id", new AttributeValue(s.id))
      .addAttributeUpdatesEntry(
        "value", new AttributeValueUpdate()
          .withValue(new AttributeValue().withN("1"))
          .withAction(AttributeAction.ADD))
      .addExpectedEntry(
        "value", new ExpectedAttributeValue()
          .withValue(new AttributeValue().withN(s.value.toString))
          .withComparisonOperator(ComparisonOperator.EQ))
  }

  private def asyncCall[T, R](p: T)(f: T => R): Future[R] = {
    Future {
      blocking {
        f(p)
      }
    }
  }

  override def nextValueFor(sequenceName: String): Future[Long] = {
    asyncCall[String, Long](sequenceName) { sequenceName =>
      val itemResult = table.client.getItem(new GetItemRequest().withTableName(table.fullName).withKey(Map("id" -> new AttributeValue().withS(sequenceName)).asJava))
      val someItem = Option(itemResult.getItem)
      someItem.fold[Long](-1l) { item =>
        val result = table.client.updateItem(createUpdateRequest(new Sequence(sequenceName, item.get("value").getN.toLong)))
        result.getAttributes.asScala("value").getN.toLong
      }
    }

    /*
    Future {
      blocking {
        val item = table.client.getItem(new GetItemRequest().withTableName(table.fullName).withKey(Map("id" -> new AttributeValue().withS(sequenceName)).asJava))
        Option(item.getItem)
      }
    } flatMap { someItem =>
      someItem.fold(Future.successful(-1l)) { item =>
        Future {
          blocking {
            val result = table.client.updateItem(createUpdateRequest(new Sequence(sequenceName, item.get("value").getN.toLong)))
            result.getAttributes.asScala("value").getN.toLong
          }
        }
      }
    }*/
    /*
    table.get(sequenceName).flatMap { p =>
      println(Thread.currentThread().getName)
      p.fold(Future.successful(-1l)) { s =>
        Future {
          blocking {
            val result = table.client.updateItem(createUpdateRequest(s))
            result.getAttributes.asScala("value").getN.toLong
          }
        }
      }
    }*/
  }

  override def saveOrUpdate(sequence: Sequence): Future[Unit] =
    table.put(sequence).map(_ => ())

  override def createIfNotExists(sequence: Sequence): Unit = {
    val item = Map(
      "id" -> new AttributeValue().withS(sequence.id),
      "value" -> new AttributeValue().withN(sequence.value.toString)
    )
    table.client.putItem {
      new PutItemRequest()
        .withTableName(table.fullName)
        .withItem(item.asJava)
        .withExpected(Map("id" -> new ExpectedAttributeValue(false)).asJava)
    }
  }
}
