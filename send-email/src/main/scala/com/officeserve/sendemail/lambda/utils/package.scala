package com.officeserve.sendemail.lambda

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

package object utils extends FutureUtils {

  def formatDate(deliveryDate: Option[ZonedDateTime], pattern: String = "EEEE dd MMMM"): String = {
    deliveryDate.fold("") { date =>
      //val date = ZonedDateTime.parse(strDate, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
      date.format(DateTimeFormatter.ofPattern(pattern))
    }
  }

}
