package officeserve.commons.spray.webutils


import java.time.{ZoneId, ZonedDateTime}

import officeserve.commons.domain.MoneyDom
import org.slf4j.LoggerFactory
import spray.json.{DefaultJsonProtocol, JsString, JsValue, RootJsonFormat}


trait CommonJsonProtocol extends DefaultJsonProtocol {

  implicit object ZonedDateTimeFormat extends RootJsonFormat[ZonedDateTime] {

    override def read(json: JsValue): ZonedDateTime = json match {
      case JsString(v) => DateSerializerUtil.dateStringToZonedDateTime(v)
      case _ => spray.json.deserializationError(" not a valid ISO date format")
    }

    override def write(date: ZonedDateTime): JsString =
      JsString(DateSerializerUtil.zonedDateTimeToString(date))
  }

  implicit val errorFormat: RootJsonFormat[Error] = jsonFormat3(Error.apply)
  implicit val dateFormat: RootJsonFormat[ZonedDateTime] = ZonedDateTimeFormat
  implicit val priceFormat: RootJsonFormat[Price] = jsonFormat2(Price.apply)
}

case class Price(value: BigDecimal, currency: String = "GBP")

object Price {
  def toPrice(moneyDom: MoneyDom) = new Price(moneyDom.amount.getAmount, moneyDom.amount.getCurrencyUnit.getCode)
}

case class Error(
                  code: Int,
                  message: Option[String],
                  fields: Option[String]
                )

object Error {
  def withLogger(
                  code: Int,
                  message: Option[String],
                  fields: Option[String],
                  e: Throwable
                ): Error = {
    val log = LoggerFactory.getLogger(Error.getClass.getName)
    log.error(e.getMessage, e)
    new Error(code, message, fields)
  }
}
