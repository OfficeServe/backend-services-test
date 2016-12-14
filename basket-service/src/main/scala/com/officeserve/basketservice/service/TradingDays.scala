package com.officeserve.basketservice.service

import java.time.format.DateTimeFormatter
import java.time.{DayOfWeek, LocalTime, ZonedDateTime}
import java.time.temporal.ChronoUnit
import java.time._

import com.officeserve.basketservice.persistence.DeliverySlot
import com.officeserve.basketservice.settings.BasketConfig
import officeserve.commons.spray.webutils.DateSerializerUtil

import scala.collection.immutable.List

/**
  * Created by mo on 06/09/2016.
  */
class TradingDays(val dateUtils: DateUtils, basketConfig: BasketConfig) {

  implicit val londonZone = ZoneId.of("Europe/London")

  private def toLondonZonedDateTime(days: List[String]): List[ZonedDateTime] =
    days.map(d => ZonedDateTime.of(LocalDateTime.parse(d), londonZone))

  // holidays until end of 2017
  val holidays: List[ZonedDateTime] =
    toLondonZonedDateTime(
      List(
        "2016-12-26T00:00:00",
        "2016-12-27T00:00:00",
        "2016-12-28T00:00:00",
        "2016-12-29T00:00:00",
        "2016-12-30T00:00:00",
        "2017-01-02T00:00:00",
        "2017-04-14T00:00:00",
        "2017-04-17T00:00:00",
        "2017-05-01T00:00:00",
        "2017-05-29T00:00:00",
        "2017-08-28T00:00:00",
        "2017-12-25T00:00:00",
        "2017-12-26T00:00:00",
        "2017-12-27T00:00:00",
        "2017-12-28T00:00:00",
        "2017-12-29T00:00:00"
      )
    )

  def isHoliday(date: ZonedDateTime): Boolean = {
    val dateWithoutTime = date.truncatedTo(ChronoUnit.DAYS)
    holidays.exists(dateWithoutTime.isEqual)
  }

  def isWeekend(date: ZonedDateTime): Boolean = {
    date.getDayOfWeek == DayOfWeek.SATURDAY || date.getDayOfWeek == DayOfWeek.SUNDAY
  }

  def getTableTopCutOffTimeFor(dateTime: ZonedDateTime): ZonedDateTime = {
    val formatter = DateTimeFormatter.ofPattern("HH:mm")

    val time = LocalTime.parse(basketConfig.tableTopCutOffTime, formatter)

    val cutoffTime = ZonedDateTime.of(dateTime.getYear,
      dateTime.getMonth.getValue,
      dateTime.getDayOfMonth,
      time.getHour, time.getMinute, 0, 0,
      londonZone)

    cutoffTime
  }

  def getTableTopCutOffTime: ZonedDateTime =
    getTableTopCutOffTimeFor(dateUtils.now)

  def getDeliveryFromDay(maxLeadTime: Int, tableTopCutOffTime: ZonedDateTime = getTableTopCutOffTime): ZonedDateTime = {
    val now = dateUtils.now
    val nextWorkingDateTime = getNextWorkingDay(now.plusDays(maxLeadTime)).withZoneSameInstant(londonZone)
    val nextWorkingDay = nextWorkingDateTime.truncatedTo(ChronoUnit.DAYS)
    if (now.isBefore(tableTopCutOffTime) && isWorkingDay(now)) {
      nextWorkingDay
    } else {
      getNextWorkingDay(nextWorkingDay.plusDays(1))
    }
  }

  def getNextWorkingDay(date: ZonedDateTime = dateUtils.now): ZonedDateTime = {
    if (isWorkingDay(date)) {
      date
    } else {
      getNextWorkingDay(date.plusDays(1))
    }
  }

  def getLastWorkingDay(dateTime: ZonedDateTime): ZonedDateTime = {
    if (isWorkingDay(dateTime)) {
      dateTime
    } else {
      getLastWorkingDay(dateTime.minusDays(1))
    }
  }

  def isWorkingDay(dateTime: ZonedDateTime):Boolean = !(isWeekend(dateTime) || isHoliday(dateTime))

  def getSlots = {

    val now = dateUtils.now

    implicit def toOffsetTime(hoursMinutes: String): OffsetTime = {
      OffsetTime.of(LocalTime.parse(hoursMinutes), londonZone.getRules.getOffset(now.toInstant))
    }

    implicit def toLocalTime(hoursMinutes: String): LocalTime = {
      DateSerializerUtil.toUTC(toOffsetTime(hoursMinutes))
    }

    List(
      DeliverySlot("06:00", "08:00"),
      DeliverySlot("07:00", "09:00"),
      DeliverySlot("08:00", "10:00"),
      DeliverySlot("09:00", "11:00"),
      DeliverySlot("10:00", "12:00"),
      DeliverySlot("11:00", "13:00"),
      DeliverySlot("12:00", "14:00"),
      DeliverySlot("13:00", "15:00"),
      DeliverySlot("14:00", "16:00"),
      DeliverySlot("15:00", "17:00"),
      DeliverySlot("16:00", "18:00")
    )
  }

  def getDeliverySlot(deliveryDate: ZonedDateTime): DeliverySlot = {
    val slots = getSlots

    getSlots.find(_.from.toString.equals(s"${"%02d".format(deliveryDate.getHour)}:${"%02d".format(deliveryDate.getMinute)}"))
      .getOrElse {
        throw InvalidRequestException("invalid delivery time slot")
      }
  }

}

case class DateUtils() {

  def now = ZonedDateTime.now

  def tomorrow = now.plusDays(1)

}

