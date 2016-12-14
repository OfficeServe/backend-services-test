package com.officeserve.reportservice

import java.time.format.{DateTimeFormatter, DateTimeParseException}
import java.time.{LocalDate, ZonedDateTime, format}

import com.gu.scanamo.DynamoFormat
import com.gu.scanamo.error.DynamoReadError
import com.officeserve.reportservice.repositories.Exceptions.DynamoReadErrorException
import officeserve.commons.spray.webutils.DateSerializerUtil

import scala.concurrent.{ExecutionContext, Future}

package object repositories {

  object Exceptions {

    case class DynamoReadErrorException(dynamoReadErrors: Seq[DynamoReadError]) extends Exception(s"Errors reading from database:\n$dynamoReadErrors")

  }

  private[repositories] val isoLocalDateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

  private[repositories] implicit val bigDecimalFormat: DynamoFormat[BigDecimal] = DynamoFormat.coercedXmap[BigDecimal, String, NumberFormatException](
    s => BigDecimal(s)
  )(
    bd => bd.toString()
  )
  private[repositories] implicit val zonedDateTimeFormat: DynamoFormat[ZonedDateTime] =
    DynamoFormat.coercedXmap[ZonedDateTime, String, DateTimeParseException](
      DateSerializerUtil.dateStringToZonedDateTime
    )(
      DateSerializerUtil.zonedDateTimeToString
    )

  private[repositories] implicit val localDateFormat: DynamoFormat[LocalDate] =
    DynamoFormat.coercedXmap[LocalDate, String, format.DateTimeParseException](
      LocalDate.parse(_, isoLocalDateFormatter)
    )(
      localDate => localDate.format(isoLocalDateFormatter)
    )


  def failOnFirstError[T](result: Future[List[Either[DynamoReadError, T]]])(implicit ec: ExecutionContext): Future[Seq[T]] =
    result.flatMap { result =>
      result.partition(_.isLeft) match {
        case (Nil, succeeded) =>
          Future.successful(for (Right(s) <- succeeded.view) yield s)
        case (failed, _) =>
          val errors = for (Left(f) <- failed.view) yield f
          Future.failed(DynamoReadErrorException(errors))
      }
    }


}
