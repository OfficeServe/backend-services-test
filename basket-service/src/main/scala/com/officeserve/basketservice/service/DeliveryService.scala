package com.officeserve.basketservice.service

import com.google.inject.{ImplementedBy, Inject}
import com.officeserve.basketservice.persistence.{PostCode, PostCodeRepository}
import officeserve.commons.spray.webutils.Error

import scala.concurrent.{ExecutionContext, Future}

/**
  * Created by mo on 23/11/2016.
  */
@ImplementedBy(classOf[DeliveryServiceImpl])
trait DeliveryService {
  def getPostcodeArea(postcode: String): Future[Either[Error, PostCode]]

  def getCoveredArea: Future[Either[Error, Set[PostCode]]]
}

class DeliveryServiceImpl @Inject()(postCodeRepo: PostCodeRepository)(implicit val ec: ExecutionContext)
  extends DeliveryService {

  override def getPostcodeArea(postcode: String): Future[Either[Error, PostCode]] =
    postCodeRepo.getPostcodeArea(postcode.iterator.takeWhile(c => c.isLetter).mkString.toUpperCase)

  override def getCoveredArea: Future[Either[Error, Set[PostCode]]] =
    postCodeRepo.getCoveredPostcodes.map {
      case Right(ps) => Right(ps)
      case Left(e) => Left(e)
    }
}
