package com.officeserve.documentservice.models

case class ProductReport(date: String, items: List[ProductReportItem])

case class ProductReportItem(productCode: String, productName: String, previous: String, difference: String, total: String, deliveryDate: String)
