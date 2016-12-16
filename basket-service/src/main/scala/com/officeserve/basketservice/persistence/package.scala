package com.officeserve.basketservice

import java.time.{LocalTime, OffsetTime, ZonedDateTime}
import java.time.format.{DateTimeFormatter, DateTimeParseException}

import cats.data.{NonEmptyList, Xor}
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import com.gu.scanamo.DynamoFormat
import com.gu.scanamo.error._
import com.officeserve.basketservice.service.DynamoDBErrorException
import officeserve.commons.domain.MoneyDom
import officeserve.commons.spray.webutils.DateSerializerUtil
import org.joda.money.IllegalCurrencyException

import scala.collection.JavaConverters._

package object persistence {

  implicit val offsetTimeFormat: DynamoFormat[LocalTime] =
    DynamoFormat.coercedXmap[LocalTime, String, DateTimeParseException](
      LocalTime.parse(_)
    )(
      _.format(DateTimeFormatter.ISO_LOCAL_TIME)
    )

  implicit val zonedDateTimeFormat: DynamoFormat[ZonedDateTime] =
    DynamoFormat.coercedXmap[ZonedDateTime, String, DateTimeParseException](
      DateSerializerUtil.dateStringToZonedDateTime
    )(
      DateSerializerUtil.zonedDateTimeToString
    )

  implicit val moneyDomFormat: DynamoFormat[MoneyDom] =
    DynamoFormat.coercedXmap[MoneyDom, MoneyStorage, IllegalCurrencyException] { ms =>
      MoneyDom.asJodaMoney(ms.amount, ms.currency.getOrElse("GBP"))
    } { md =>
      // For now, we don't bother to store the currency, because it will always be GBP
      MoneyStorage(md.amount.getAmount)
    }

  implicit object OrderStatusFormat extends DynamoFormat[OrderStatus] {
    override def read(av: AttributeValue) =
      for {
        result <- av.getS() match {
          case "Pending" => Xor.Right(Pending)
          case "Processed" => Xor.Right(Processed)
          case "Started" => Xor.Right(Started)
          case "Cancelled" => Xor.Right(Cancelled)
          case _ => Xor.Left(MissingProperty)
        }
      } yield result

    override def write(os: OrderStatus) = {
      new AttributeValue().withS(os.status)
    }
  }

  implicit object CoverageStatusFormat extends DynamoFormat[CoverageStatus] {
    override def read(av: AttributeValue) =
      for {
        result <- av.getS() match {
          case "Covered" => Xor.Right(Covered)
          case "UpComing" => Xor.Right(UpComing)
          case _ => Xor.Left(MissingProperty)
        }
      } yield result

    override def write(os: CoverageStatus): AttributeValue = {
      new AttributeValue().withS(os.status)
    }
  }

  implicit object PaymentStatusFormat extends DynamoFormat[PaymentStatus] {
    override def read(av: AttributeValue) =
      for {
        osm <- Xor.fromOption(Option(av.getM()), NoPropertyOfType("M", av))
        status <- Xor.fromOption(osm.asScala.get("status"), InvalidPropertiesError(NonEmptyList(PropertyReadError("status", MissingProperty))))
        statusStr <- Xor.fromOption(Option(status.getS()), InvalidPropertiesError(NonEmptyList(PropertyReadError("status", NoPropertyOfType("S", status)))))
        result <- statusStr match {
          case "Succeeded" => Xor.Right(Succeeded)
          case "Verified" => Xor.Right(Verified)
          case "Failed" => DynamoFormat[Failed].read(av)
          case _ => throw DynamoDBErrorException(s"can't deserialize PaymentStatus= ${statusStr} from the database")
        }
      } yield result

    override def write(os: PaymentStatus) = {
      val topLevelProps = os match {
        case s@Succeeded => DynamoFormat[Succeeded.type].write(s)
        case f@Failed(_) => DynamoFormat[Failed].write(f)
        case i@Verified => DynamoFormat[Verified.type].write(i)
        case _ => throw DynamoDBErrorException(s"can't serialize PaymentStatus= ${os} to the database")
      }
      val allProps =
        topLevelProps.getM.asScala + ("status" -> DynamoFormat[String].write(os.status))
      new AttributeValue().withM(allProps.asJava)
    }
  }



  implicit object TablewareFormat extends DynamoFormat[Tableware] {
    override def read(av: AttributeValue) =
      for {
        osm <- Xor.fromOption(Option(av.getM()), NoPropertyOfType("M", av))
        preferenceType <- Xor.fromOption(osm.asScala.get("preferenceType"), InvalidPropertiesError(NonEmptyList(PropertyReadError("preferenceType", MissingProperty))))
        preferenceTypeStr <- Xor.fromOption(Option(preferenceType.getS()), InvalidPropertiesError(NonEmptyList(PropertyReadError("status", NoPropertyOfType("S", preferenceType)))))
        result <- preferenceTypeStr match {
          case "NoTableware" => DynamoFormat[NoTableware].read(av)
          case "Napkins" => DynamoFormat[Napkins].read(av)
          case "NapkinsPlates" => DynamoFormat[NapkinsPlates].read(av)
          case "NapkinsPlatesCups" => DynamoFormat[NapkinsPlatesCups].read(av)
          case _ => throw DynamoDBErrorException(s"can't deserialize Tableware= ${preferenceTypeStr} from the database")
        }
      } yield result

    override def write(os: Tableware) = {
      val topLevelProps = os match {
        case n: NoTableware => DynamoFormat[NoTableware].write(n)
        case n: Napkins => DynamoFormat[Napkins].write(n)
        case n: NapkinsPlates => DynamoFormat[NapkinsPlates].write(n)
        case n: NapkinsPlatesCups => DynamoFormat[NapkinsPlatesCups].write(n)
        case _ => throw DynamoDBErrorException(s"can't serialize Tableware= ${os} to the database")
      }
      val allProps =
        topLevelProps.getM.asScala + ("preferenceType" -> DynamoFormat[String].write(os.preferenceType)) +
          ("entitled" -> DynamoFormat[Int].write(os.entitled)) + ("selected" -> DynamoFormat[Int].write(os.selected))
      new AttributeValue().withM(allProps.asJava)
    }
  }



  implicit val basketItemFormat = DynamoFormat[BasketItem]
  implicit val basketFormat = DynamoFormat[Basket]
  implicit val addressFormat = DynamoFormat[Address]
  implicit val orderFormat = DynamoFormat[Order]
  implicit val paymentMethodFormat = DynamoFormat[PaymentMethod]
  implicit val postCodeFormat = DynamoFormat[PostCode]

  implicit val sequenceFormat = DynamoFormat[Sequence]

}
