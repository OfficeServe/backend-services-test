package com.officeserve.reportservice.models

import java.time.LocalDate

import com.officeserve.basketservice.web.OrderMessageRep

package object orderlog {

  case class OrderLog(cutOffDate: LocalDate, eventId: String, orderMessageRep: OrderMessageRep)

}
