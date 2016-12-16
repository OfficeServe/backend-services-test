package com.officeserve.documentservice.marshallers

import akka.http.scaladsl.marshalling._
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import com.officeserve.documentservice.models.{Order, ProductReport}
import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import org.json4s.{DefaultFormats, jackson}

import collection.immutable.Seq


trait JsonSupport {
  implicit val serialization = jackson.Serialization
  // Add more formats here if needed
  implicit val formats = DefaultFormats


  implicit val invoiceUnMarshaller: FromEntityUnmarshaller[Order] = Json4sSupport.json4sUnmarshaller
  implicit val invoiceMarshaller: ToEntityMarshaller[Order] = Json4sSupport.json4sMarshaller

  implicit val seqInvoiceUnMarshaller: FromEntityUnmarshaller[Seq[Order]] = Json4sSupport.json4sUnmarshaller
  implicit val seqinvoiceMarshaller: ToEntityMarshaller[Seq[Order]] = Json4sSupport.json4sMarshaller

  implicit val productReportUnMarshaller: FromEntityUnmarshaller[ProductReport] = Json4sSupport.json4sUnmarshaller
  implicit val productReportMarshaller: ToEntityMarshaller[ProductReport] = Json4sSupport.json4sMarshaller

}


