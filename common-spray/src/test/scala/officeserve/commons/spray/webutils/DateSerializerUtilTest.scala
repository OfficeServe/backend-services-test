package officeserve.commons.spray.webutils

import java.time.{ZoneId, ZoneOffset, ZonedDateTime}

import org.scalatest.{FlatSpec, Matchers}
import spray.json.SerializationException

class DateSerializerUtilTest extends FlatSpec with Matchers {
  val inputDateString = "2016-06-16T12:11:06+01:00"
  val inputZonedDateTime = ZonedDateTime.of(2016, 6, 16, 12, 11, 6, 0, ZoneOffset.of("+01:00"))

  "DateSerialiserUtil" should "convert a correct String date to ZonedTimeDate " in {
    val zonedDateTime = DateSerializerUtil.dateStringToZonedDateTime(inputDateString)
    zonedDateTime.toInstant shouldBe inputZonedDateTime.toInstant
  }

  it should "convert ZonedTimeDate to correct String date format" in {
    val stringZonedDateTime = DateSerializerUtil.zonedDateTimeToString(inputZonedDateTime)
    ZonedDateTime.parse(stringZonedDateTime).toInstant shouldBe ZonedDateTime.parse(inputDateString).toInstant
  }

  it should "produce SerializationException when given incorrect dateformat String" in {
    intercept[SerializationException] {
      DateSerializerUtil.dateStringToZonedDateTime("3 Jun 2016 11:05")
    }
  }
}
