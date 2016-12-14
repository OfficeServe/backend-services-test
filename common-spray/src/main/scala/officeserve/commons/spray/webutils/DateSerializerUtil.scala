package officeserve.commons.spray.webutils

import java.time._
import java.time.format.DateTimeFormatter

import scala.util.Try

object DateSerializerUtil {
  private val formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

  def zonedDateTimeToString(date: ZonedDateTime): String = {
    date.withZoneSameInstant(ZoneId.of("UTC")).withNano(0).format(formatter)
  }

  def dateStringToZonedDateTime(stringDate: String): ZonedDateTime = {
    Try[ZonedDateTime](ZonedDateTime.parse(stringDate, formatter).withZoneSameInstant(ZoneId.of("UTC")))
      .getOrElse(spray.json.serializationError(s"'$stringDate' is not a valid ISO date format"))
  }

  def toUTC(time: OffsetTime): LocalTime = {
    val offsetInSeconds = time.getOffset.getTotalSeconds
    time.toLocalTime.minusSeconds(offsetInSeconds)
  }

  implicit def fromStringToLocalTime(hoursMinutes: String):LocalTime =
    LocalTime.parse(hoursMinutes, DateTimeFormatter.ISO_LOCAL_TIME)

  implicit def fromLocalTimeToString(localTime: LocalTime): String =
    localTime.withNano(0).format(DateTimeFormatter.ISO_LOCAL_TIME)


}
