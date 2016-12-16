package com.officeserve.basketservice.service


import com.officeserve.basketservice.persistence.{InvoiceAddress, PaymentMethod, PaymentMethodRepository}
import com.officeserve.basketservice.web.{PaymentMethodType, UserRep}
import com.typesafe.scalalogging.StrictLogging
import officeserve.commons.spray.webutils.Error

import scala.concurrent.{ExecutionContext, Future}


/**
  * Created by mo on 14/11/2016.
  */
//TODO write unit tests for this admin once being used from places other than the migration tools
trait PaymentMethodServiceAdmin {
  def giveOnAccountPrivilege(users: Set[OnAccountUser]): Future[Either[Error, Unit]]

  def removeAccountPrivilege(users: Set[OnAccountUser]): Future[Either[Error, Unit]]

}

class PaymentMethodServiceAdminImpl(paymentMethodRepository: PaymentMethodRepository)(implicit ec: ExecutionContext) extends PaymentMethodServiceAdmin with StrictLogging {
  override def giveOnAccountPrivilege(users: Set[OnAccountUser]): Future[Either[Error, Unit]] = {

    // First check if this username has already a paymentMethod OnAcccount saved,
    // if yes ignore else create a new onAccount
    val paymentMethods: Set[Future[Either[Error, Option[PaymentMethod]]]] = users.map { user =>
      paymentMethodRepository.getPaymentMethodsByUsername(user.email).map {
        case Right(pms) =>
          if (!pms.exists(_.paymentType == PaymentMethodType.onAccountType)) {
            Right(Some(pms
              .headOption
              .map(p => createPaymentMethod(user, p.userId))
              .getOrElse(createPaymentMethod(user, UserRep.unknownUserId))))
          }
          else {
            Right(None)
          }
        case Left(e) =>
          logger.error(s"failed to read from database for :  ${user.email} ", e)
          Left(e)
      }
    }
    val readResult = Future.fold(paymentMethods)(Set[PaymentMethod]()) { (acc, curr) =>
      curr match {
        case Right(pm) => pm.map(s => acc + s).getOrElse(acc)
        case Left(e) => acc
      }
    }

    readResult.flatMap(pms => paymentMethodRepository.batchCreatePaymentMethods(pms))

  }


  private def createPaymentMethod(user: OnAccountUser, userId: String): PaymentMethod = {
    logger.info(s"creating PaymentMethod for username : ${user.email}")
    PaymentMethod(userId = userId,
      username = user.email,
      paymentType = PaymentMethodType.onAccountType,
      isDefault = true,
      label = PaymentMethod.label(PaymentMethodType.onAccountType),
      invoiceAddress = user.address)
  }

  override def removeAccountPrivilege(users: Set[OnAccountUser]): Future[Either[Error, Unit]] = ???
}

case class OnAccountUser(email: String, address: InvoiceAddress)