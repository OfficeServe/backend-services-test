package com.officeserve.basketservice.persistence

import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType._
import com.amazonaws.services.dynamodbv2.model._
import com.google.inject.ImplementedBy
import com.gu.scanamo.syntax._
import com.officeserve.basketservice.common.{DynamoDBSettings, DynamoDBSupport, DynamoDBTable, FilterCondition}
import com.typesafe.scalalogging.StrictLogging
import officeserve.commons.spray.webutils.Error

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Left
import DynamoDBTable._

/**
  * Created by mo on 05/10/2016.
  */
@ImplementedBy(classOf[PaymentMethodRepositoryImpl])
trait PaymentMethodRepository {

  def getPaymentMethodById(id: String): Future[Either[Error, PaymentMethod]]

  def savePaymentMethod(paymentMethod: PaymentMethod): Future[Either[Error, PaymentMethod]]

  def getPaymentMethodByToken(token: String): Future[Either[Error, PaymentMethod]]

  def getPaymentMethodsByUsername(userId: String, userName: String): Future[Either[Error, Set[PaymentMethod]]]

  /**
    * only used for On_account paymentMethod creation in [[com.officeserve.basketservice.service.PaymentMethodServiceAdmin]]
    */
  def getPaymentMethodsByUsername(userName: String): Future[Either[Error, Set[PaymentMethod]]]

  def batchCreatePaymentMethods(paymentMethods: Set[PaymentMethod]): Future[Either[Error, Unit]]

  def getPaymentMethodsByUserId(userId: String): Future[Either[Error, Set[PaymentMethod]]]

  def updatePaymentMethodUserID(username: String, userId: String): Future[Either[Error, Unit]]

  def updatePaymentMethodInvoiceAddress(id: String, invoiceAddress: InvoiceAddress): Future[Either[Error, Unit]]

  def deletePaymentMethod(id: String): Future[Either[Error, Unit]]

}

class PaymentMethodRepositoryImpl(implicit settings: DynamoDBSettings, executor: ExecutionContext)
  extends DynamoDBSupport[PaymentMethod](DynamoDBTable[PaymentMethod]("paymentMethods",
    new ProvisionedThroughput(5l, 5l),
    'id -> S)()('username -> S, 'token -> S)(globalSecondaryIndexes("username", "token")))
    with PaymentMethodRepository
    with StrictLogging {

  val DefaultLimit = 100

  override def getPaymentMethodById(id: String): Future[Either[Error, PaymentMethod]] =
    table.get(('id -> id)).map { s =>
      s.toRight(Error(404, Some(s"paymentMethod id=$id not found"), None))
    } recover {
      case e => Left(Error.withLogger(500, Some("internal server error"), None, e))
    }

  override def savePaymentMethod(paymentMethod: PaymentMethod): Future[Either[Error, PaymentMethod]] = {

    // if is a new default payment method update existing so we only have 1 default within the collection
    if (paymentMethod.isDefault) {
      getDefaultPaymentMethods(paymentMethod.userId).flatMap {
        case Right(defaults) => {
          //If the paymentMethod was already default, we don't want to have duplicate elements
          val updatedDpms = defaults.filter(_.id != paymentMethod.id).map(dpms => dpms.copy(isDefault = false))
          table.putAll(paymentMethod :: updatedDpms.toList) map { _ =>
            logger.debug(s"saved new default payment id=${paymentMethod.id}")
            Right(paymentMethod)
          } recover {
            case e => Left(Error.withLogger(500, Some("internal server error"), None, e))
          }
        }
        case Left(e) => Future.successful(Left(e))
      } recover {
        case e => Left(Error.withLogger(500, Some("internal server error"), None, e))
      }
    } else {
      table.put(paymentMethod).map {
        _ =>
          logger.debug(s"saved new payment id=${paymentMethod.id}")
          Right(paymentMethod)
      } recover {
        case e => Left(Error.withLogger(500, Some("internal server error"), None, e))
      }
    }
  }

  override def getPaymentMethodByToken(token: String): Future[Either[Error, PaymentMethod]] =
    table.filter[Set](Map("token" -> new Condition().withComparisonOperator(ComparisonOperator.EQ)
      .withAttributeValueList(new AttributeValue().withS(token))), DefaultLimit)
      .map(p => p.headOption.toRight(Error(404, Some(s"Payment method with token $token not found"), None))) recover {
      case e => Left(Error.withLogger(500, Some("internal server error"), None, e))
    }

  override def getPaymentMethodsByUsername(userId: String, userName: String): Future[Either[Error, Set[PaymentMethod]]] = {
    val conditions = Set(FilterCondition("username", userName),
      FilterCondition("userId", userId))
    table.filters[Set](conditions)
  }

  override def getPaymentMethodsByUserId(userId: String): Future[Either[Error, Set[PaymentMethod]]] =
    table.filter[Set](Map("userId" -> new Condition().withComparisonOperator(ComparisonOperator.EQ)
      .withAttributeValueList(new AttributeValue().withS(userId))), DefaultLimit)
      .map { p =>
        logger.debug(s"${p.size} paymentMethods found")
        Right(p)
      } recover {
      case e => Left(Error.withLogger(500, Some("internal server error"), None, e))
    }

  private def getDefaultPaymentMethods(userId: String): Future[Either[Error, Set[PaymentMethod]]] =
    table.filter[Set](Map("isDefault" -> new Condition().withComparisonOperator(ComparisonOperator.EQ)
      .withAttributeValueList(new AttributeValue().withBOOL(true)),
      "userId" -> new Condition().withComparisonOperator(ComparisonOperator.EQ)
        .withAttributeValueList(new AttributeValue().withS(userId))), DefaultLimit)
      .map(p => Right(p)) recover {
      case e => Left(Error.withLogger(500, Some("internal server error"), None, e))
    }

  override def updatePaymentMethodUserID(id: String, userId: String): Future[Either[Error, Unit]] =
    table.executeOperation(table.scanamoTable.update('id -> id, set('userId -> userId)))
      .map(_ => Right(())) recover {
      case e => Left(Error.withLogger(500, Some("internal server error"), None, e))
    }

  override def updatePaymentMethodInvoiceAddress(id: String, invoiceAddress: InvoiceAddress): Future[Either[Error, Unit]] =
    table.executeOperation(table.scanamoTable.update('id -> id, set('invoiceAddress -> invoiceAddress)))
      .map(_ => Right(())) recover {
      case e => Left(Error.withLogger(500, Some("internal server error"), None, e))
    }

  override def deletePaymentMethod(id: String): Future[Either[Error, Unit]] =
    table.delete(id) map (_ => Right(())) recover {
      case e => Left(Error.withLogger(500, Some("internal server error"), None, e))
    }

  /**
    * only used for On_account paymentMethod creation in [[com.officeserve.basketservice.service.PaymentMethodServiceAdmin]]
    */
  override def getPaymentMethodsByUsername(userName: String): Future[Either[Error, Set[PaymentMethod]]] =
  table.filter[Set](Map("username" -> new Condition().withComparisonOperator(ComparisonOperator.EQ)
    .withAttributeValueList(new AttributeValue().withS(userName))), DefaultLimit)
    .map { p =>
      logger.debug(s"${p.size} paymentMethods found")
      Right(p)
    } recover {
    case e => Left(Error.withLogger(500, Some("internal server error"), None, e))
  }

  override def batchCreatePaymentMethods(paymentMethods: Set[PaymentMethod]): Future[Either[Error, Unit]] =
    table.putAll(paymentMethods.toList).map(_ => Right(())) recover {
      case e => Left(Error.withLogger(500, Some("internal server error"), None, e))
    }
}
