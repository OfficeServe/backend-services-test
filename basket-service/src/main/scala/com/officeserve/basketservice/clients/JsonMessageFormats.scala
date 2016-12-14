package com.officeserve.basketservice.clients

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import com.officeserve.basketservice.persistence.{Cancelled, OrderStatus, Pending, Processed, Started}
import org.json4s.JsonAST.{JField, JObject, JString}
import org.json4s.{CustomSerializer, MappingException, _}
import org.json4s.jackson.Serialization

trait JsonMessageFormats {

  implicit val formats =
    Serialization.formats(NoTypeHints) + OrderStatusSerializer + ZonedDateTimeSerializer + EventTypeSerializer

  object OrderStatusSerializer extends Serializer[OrderStatus] {

    override def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, JValue), OrderStatus] = {
      case (_, json) => json match {
        case (JObject(JField("status", JString("Processed")) :: Nil)) =>
          Processed
        case (JObject(JField("status", JString("Started")) :: Nil)) =>
          Started
        case (JObject(JField("status", JString("Pending")) :: Nil)) =>
          Pending
        case (JObject(JField("status", JString("Cancelled")) :: Nil)) =>
          Cancelled
      }
    }

    override def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
      case orderStatus: OrderStatus => orderStatus match {
        case _ => JObject(JField("status", JString(orderStatus.status)) :: Nil)
      }
    }
  }

  case object EventTypeSerializer extends CustomSerializer[EventType](format => ( {
    case JString(str) => str match {
      case _ => throw new MappingException("Can't convert " + str + " to " + classOf[EventType])
    }
  }, {
    case et: EventType => JString(et.getClass.getSimpleName.replaceAll("\\$",""))
  }))

  case object ZonedDateTimeSerializer extends CustomSerializer[ZonedDateTime](format => ( {
    case JString(strDate) => ZonedDateTime.parse(strDate, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
  }, {
    case zdt: ZonedDateTime => JString(zdt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
  }))

}

object JsonMessageFormats extends JsonMessageFormats
