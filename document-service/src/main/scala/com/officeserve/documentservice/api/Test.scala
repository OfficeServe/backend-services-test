package com.officeserve.documentservice.api

import com.officeserve.documentservice.models._

import scala.collection.immutable.Seq
import scala.concurrent.ExecutionContext.Implicits._
import scala.util.{Failure, Success}

object Test extends App {

  val address = Address("Music Building", Some("Rock Rd"), Some("Yeah Lane"), "R0 CK", "London", Some("Leave it wherever you want"))
  val invoiceTo = InvoiceTo("Frank Zappa", address, "123456", "frank.zappa@gmail.com")

  val invoiceDetails = InvoiceDetails("01/01/2016", "1234", None, "Jimmy Page", Some("09876"), Some("123"), "On Account", Some("Carrier Pigeon"))
  val deliveryAddress = DeliveryAddress(None, "John Petrucci", address)

  val deliveryDate = Some("Fri 17 Jun 2016")
  val deliverySlot = "09:00-11:00"
  val basket = Basket("£1", "£1", "£1", "£1", "£1", Some(Promo("25% first order discount", "£1")), List(Item("BLT", "BG123-POP", "1", "£1", "£1", "£1", "£1")))
  val invoice = Order(invoiceTo, invoiceDetails, deliveryAddress, deliveryDate, deliverySlot, basket, Some(Tableware(12, 12, 0)))


  private val productReportItems = List(
    ProductReportItem("AB-123", "Classic Sandwich Selection", "12", "+3", "15","Fri 17 Jun 2016"),
    ProductReportItem("AB-124", "Mediterranean Roasted Vegetables & Pasta", "9", "+2", "11","Fri 17 Jun 2016"),
    ProductReportItem("AB-125", "Middle Eastern-style Dips with Crudités", "15", "-3", "12","Fri 17 Jun 2016")
  )
  val productReport = ProductReport("Thu 16 Jun 2016 10:59", productReportItems)


  //  val documentServiceApi = new DocumentServiceApi2()
  val documentServiceApi = DocumentServiceApi()

  documentServiceApi.generateInvoicePdf(invoice)
    .onComplete {
      case Success(url) => println(url)
      case Failure(t) => println(t)
    }

  documentServiceApi.generateReceiptPdf(invoice)
    .onComplete {
      case Success(url) => println(url)
      case Failure(t) => println(t)
    }

  documentServiceApi.generateDeliveryNotePdf(invoice)
    .onComplete {
      case Success(url) => println(url)
      case Failure(t) => println(t)
    }

  documentServiceApi.generateDeliveryManifestPdf(Seq(invoice, invoice, invoice))
    .onComplete {
      case Success(url) => println(url)
      case Failure(t) => println(t)
    }

  documentServiceApi.generateProductReportPdf(productReport)
    .onComplete {
      case Success(url) => println(url)
      case Failure(t) => println(t)
    }


  documentServiceApi.generateInvoiceEmail(invoice)
    .onComplete {
      case Success(html) => println(html)
      case Failure(t) => println(t)
    }

  documentServiceApi.generateReceiptEmail(invoice)
    .onComplete {
      case Success(html) => println(html)
      case Failure(t) => println(t)
    }

  documentServiceApi.generateCancellationEmail(invoice)
    .onComplete {
      case Success(html) => println(html)
      case Failure(t) => println(t)
    }

  documentServiceApi.generateDeliveryNoteEmail()
    .onComplete {
      case Success(html) => println(html)
      case Failure(t) => println(t)
    }

  documentServiceApi.generateProductReportEmail()
    .onComplete {
      case Success(html) => println(html)
      case Failure(t) => println(t)
    }




  sys.addShutdownHook(documentServiceApi.close())

}
