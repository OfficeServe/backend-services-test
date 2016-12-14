package com.officeserve.reportservice

package object models {


  sealed abstract class EventType

  // basket-service
  case object PurchaseComplete extends EventType

  case object PurchaseCancelled extends EventType

  case object PartialProductReportTime extends EventType

  case object CutOffTimeProcessOrders extends EventType

  case object CutOffTime extends EventType


  // report-service
  case object StoreOrders extends EventType

  case object SendOrderDocuments extends EventType

  case object SendCancellationDocuments extends EventType

  // send-email-lambda
  case object SendEmail extends EventType


  // Generic Event
  case class Event[T <: EventType, E](eventType: T, entities: Set[E])

}
