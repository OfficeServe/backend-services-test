package com.officeserve.basketservice.persistence

import cats.data.Xor
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import com.gu.scanamo.DynamoFormat
import com.gu.scanamo.error.{DynamoReadError, NoPropertyOfType}

private[persistence] case class MoneyStorage(amount: java.math.BigDecimal, currency: Option[String] = None)

private[persistence] object MoneyStorage {

  /** @todo move to common library */
  implicit object BigDecimalFormat extends DynamoFormat[java.math.BigDecimal] {
    override def read(av: AttributeValue): Xor[DynamoReadError, java.math.BigDecimal] = {
      Xor.fromOption(Option(av.getN()), NoPropertyOfType("N", av)).flatMap { bds =>
        Xor.catchOnly[NumberFormatException](new java.math.BigDecimal(bds)).leftMap { e =>
          NoPropertyOfType("BigDecimal", av)
        }
      }
    }

    override def write(bd: java.math.BigDecimal) =
      new AttributeValue().withN(bd.toPlainString())
  }

  implicit val moneyStorageFormat = DynamoFormat[MoneyStorage]

}
