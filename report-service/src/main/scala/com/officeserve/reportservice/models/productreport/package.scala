package com.officeserve.reportservice.models

import java.time.{LocalDate, ZonedDateTime}

package object productreport {
  type ProductId = String

  type ProductReportData = Map[ProductId, ProductReportEntry]

  case class ProductReportEntry(productId: String, productCode: String, productName: String, previousQuantity: Int, currentQuantity: Int, deliveryDate: ZonedDateTime)

  case class ProductReport(cutOffDate: LocalDate, generatedOn: ZonedDateTime, report: ProductReportData)

}
