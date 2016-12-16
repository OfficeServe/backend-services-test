package com.officeserve.basketservice.service

import cats.data.Validated.{invalidNel, valid}
import cats.data.{NonEmptyList, Validated, ValidatedNel, XorT}
import cats.implicits._
import com.google.inject.{ImplementedBy, Inject}
import com.officeserve.basketservice.clients.PaymentGateway
import com.officeserve.basketservice.persistence.{Address, CardPayment, InvoiceAddress, PaymentMethod, PaymentMethodRepository}
import com.officeserve.basketservice.web.{Adapter, CreateOrUpdatePaymentMethod, PaymentMethodType, UserRep}
import com.typesafe.scalalogging.StrictLogging
import officeserve.commons.spray.webutils.Error

import scala.collection.immutable.SortedSet
import scala.concurrent.{ExecutionContext, Future}

/**
  * Created by mo on 05/10/2016.
  */
@ImplementedBy(classOf[PaymentMethodServiceImpl])
trait PaymentMethodService {
  def update(userId: String, id: String, createPaymentMethod: CreateOrUpdatePaymentMethod): Future[Either[Error, PaymentMethod]]

  def createPaymentMethod(userId: String, createPayment: CreateOrUpdatePaymentMethod): Future[Either[Error, PaymentMethod]]

  def getPaymentMethods(userId: String, username: String): Future[Either[Error, Set[PaymentMethod]]]

  def makeDefaultPaymentMethod(id: String): Future[Either[Error, Unit]]

  def updatePaymentMethodInvoiceAddress(id: String, invoiceAddress: InvoiceAddress): Future[Either[Error, Unit]]

  def redactPaymentMethod(id: String, paymenMethodToken: String): Future[Either[Error, Unit]]

}

class PaymentMethodServiceImpl @Inject()(paymentMethodRepository: PaymentMethodRepository, paymentGateway: PaymentGateway) extends PaymentMethodService
  with Adapter with StrictLogging with PaymentMethodValidation {

  implicit val ec = ExecutionContext.Implicits.global

  override def createPaymentMethod(userId: String, createPayment: CreateOrUpdatePaymentMethod): Future[Either[Error, PaymentMethod]] = {
    // first check if we already persisted this token before

    createPayment.token match {
      case Some(token) => paymentMethodRepository.getPaymentMethodByToken(token).flatMap {

        case r@Right(existingPM) => Future.successful(r)

        case Left(e) if e.code == 404 => verifyAndRetainNewCard(userId, token, createPayment)

        case Left(e) => Future.successful(Left(e))

      }
      case None => Future.successful(Left(Error(400, Some("Required field 'token' is not present"), None)))
    }

  }

  override def getPaymentMethods(userId: String, username: String): Future[Either[Error, Set[PaymentMethod]]] = {
    paymentMethodRepository.getPaymentMethodsByUserId(userId).flatMap {
      case Right(pmsByUserId) =>
        val paymentMethods = if (pmsByUserId.nonEmpty) {
          Future.successful(Right(pmsByUserId.filterNot(p => p.cardPayment.exists(_.toBeRetained == false))))
        } else {
          getPaymentMethodsByUsernameAndUpdateUserId(userId, username)
        }
        val ordering = new Ordering[PaymentMethod] {
          override def compare(x: PaymentMethod, y: PaymentMethod): Int = (isOnAccount(x),isOnAccount(y)) match {
            case (true,_) => -1
            case (false,true) => 1
            case _ => (x.label + x.id).compareTo(y.label + y.id)
          }
        }
        paymentMethods.map(_.rightMap{ s =>
          println(s.size)
          val ss = SortedSet(s.toSeq:_*)(ordering)
          println(ss.size)
          ss
        })
      case Left(e) => Future.successful(Left(e))
    }
  }


  override def makeDefaultPaymentMethod(id: String): Future[Either[Error, Unit]] = {
    paymentMethodRepository.getPaymentMethodById(id).flatMap {
      case Right(p) => paymentMethodRepository.savePaymentMethod(p.copy(isDefault = true)).map {
        case Right(_) => Right(())
        case Left(e) => Left(e)
      }
      case Left(e) => Future.successful(Left(e))
    }
  }

  private def verifyAndRetainNewCard(userId: String, token: String, createPayment: CreateOrUpdatePaymentMethod) =
    paymentGateway.verifyAndRetain(token, true).flatMap {
      case Right(spreedlyResponse) => {
        val card = Some(CardPayment(token, spreedlyResponse.cardType, spreedlyResponse.truncatedCardRep, true))
        paymentMethodRepository.savePaymentMethod(
          PaymentMethod(paymentType = PaymentMethodType.creditCardType,
            userId = userId,
            token = createPayment.token,
            username = createPayment.username,
            isDefault = createPayment.isDefault.getOrElse(false),
            cardPayment = card,
            label = PaymentMethod.label(PaymentMethodType.creditCardType, card),
            invoiceAddress = createPayment.invoiceAddress))
      }

      case Left(error) => Future.successful(Left(error))
    }

  private def getPaymentMethodsByUsernameAndUpdateUserId(userId: String, username: String): Future[Either[Error, Set[PaymentMethod]]] = {

    val pmsByUsername = paymentMethodRepository.getPaymentMethodsByUsername(UserRep.unknownUserId, username)

    pmsByUsername flatMap {
      case Right(pms) =>
        Future.sequence(pms.map(pm => paymentMethodRepository.updatePaymentMethodUserID(pm.id, userId))).map(_ => Right(pms))
      case Left(e) => logger.error("failed to update paymentMethods with userId", e)
        Future.successful(Left(e))
    }
  }

  private def updateExisting(userId: String,
                             paymentMethod: PaymentMethod,
                             createPaymentMethod: CreateOrUpdatePaymentMethod): Future[Either[Error, PaymentMethod]] = {

    val updatedPaymentMethod = paymentMethod.copy(
      token = createPaymentMethod.token,
      invoiceAddress = createPaymentMethod.invoiceAddress,
      isDefault = createPaymentMethod.isDefault.getOrElse(paymentMethod.isDefault)
    )

    (for {
      _ <- XorT(Future.successful(validatePaymentMethod(userId, paymentMethod, updatedPaymentMethod).toXor))
      resp <- XorT(paymentMethodRepository.savePaymentMethod(updatedPaymentMethod).map(_.toXor))
    } yield resp).value.map(_.toEither)

  }

  override def update(userId: String,
                      id: String,
                      createPaymentMethod: CreateOrUpdatePaymentMethod): Future[Either[Error, PaymentMethod]] = {
    (for {
      paymentById           <- XorT(paymentMethodRepository.getPaymentMethodById(id).map(_.toXor))
      updatedPaymentMethod  <- XorT(updateExisting(userId, paymentById, createPaymentMethod).map(_.toXor))
    } yield updatedPaymentMethod).value.map(_.toEither)
  }

  override def updatePaymentMethodInvoiceAddress(id: String, invoiceAddress: InvoiceAddress): Future[Either[Error, Unit]] =
    paymentMethodRepository.updatePaymentMethodInvoiceAddress(id, invoiceAddress)

  override def redactPaymentMethod(id: String, paymentMethodToken: String): Future[Either[Error, Unit]] = {
    paymentGateway.redact(paymentMethodToken)
    paymentMethodRepository.deletePaymentMethod(id)
  }

}

trait PaymentMethodValidation {

  implicit class ErrorExt(thiz: Error) {
    def +(other: Error): Error = (thiz, other) match {
      case (o1, o2) if o1.code == o2.code => thiz
    }
  }

  private def combineErrors(e1: Error, e2: Error): Error = {
    //TODO: Just returning the first error for now.
    // In the future, this logic will allow us to show many errors to a user.
    e1.copy(message = e1.message.map(m => m + e2.message.map(m2 => "\n" + m2).getOrElse("")))
  }

  protected def isOnAccount(pm: PaymentMethod): Boolean =
    pm.paymentType == PaymentMethodType.onAccountType

  def validatePaymentMethod(userId: String,
                            paymentMethod: PaymentMethod,
                            updatedPaymentMethod: PaymentMethod): Validated[Error, PaymentMethod] = {

    (validateUpdateRights(paymentMethod, updatedPaymentMethod) product validateUser(userId, paymentMethod) product validateToken(paymentMethod, updatedPaymentMethod)) map {
      case ((_, _), updatedPaymentMethod) => updatedPaymentMethod
    } leftMap { nel =>
      nel.unwrap.reduce(combineErrors)
    }
  }

  def validateUpdateRights(paymentMethod: PaymentMethod, updatedPaymentMethod: PaymentMethod): ValidatedNel[Error, PaymentMethod] = {

    val onAccount = isOnAccount(paymentMethod)
    val isNewAddress = paymentMethod.invoiceAddress != updatedPaymentMethod.invoiceAddress

    if (onAccount && isNewAddress) {
      println(s"${paymentMethod.invoiceAddress} | ${updatedPaymentMethod.invoiceAddress}")
      invalidNel(Error(403, Some("Cannot update on account payment method details."), None))
    } else {
      valid(paymentMethod)
    }
  }

  def validateUser(userId: String,
                   paymentMethod: PaymentMethod): ValidatedNel[Error, String] =
    if (paymentMethod.userId != userId) {
      invalidNel(Error(409,
        Some("Cannot update paymentMethod."),
        None
      ))
    } else {
      valid(userId)
    }

  def validateToken(paymentMethod: PaymentMethod,
                    updatedPaymentMethod: PaymentMethod): ValidatedNel[Error, PaymentMethod] =
    if (paymentMethod.token != updatedPaymentMethod.token) {
      invalidNel(Error(409,
        Some("Cannot update token. Create a new Payment Method with different credit card information"),
        None
      ))
    } else {
      valid(updatedPaymentMethod)
    }


}
