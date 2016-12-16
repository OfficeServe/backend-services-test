package com.officeserve.documentservice.models

case class Order(invoiceTo: InvoiceTo,
                 invoiceDetails: InvoiceDetails,
                 deliveryAddress: DeliveryAddress,
                 deliveryDate: Option[String],
                 deliverySlot: String,
                 basket: Basket,
                 tableware: Option[Tableware] = None)

case class InvoiceTo(name: String,
                     address: Address,
                     phone: String,
                     email: String)

case class InvoiceDetails(date: String,
                          invoiceNumber: String,
                          companyName: Option[String],
                          contactName: String,
                          paymentReference: Option[String] = None,
                          accountNumber: Option[String] = None,
                          paymentMethod: String,
                          shippingMethod: Option[String] = None
                         )

case class DeliveryAddress(companyName: Option[String],
                           name: String,
                           address: Address)

case class Basket(totalPrice: String,
                  deliveryCharge: String,
                  totalVat: String,
                  orderTotal: String,
                  invoiceTotal: String,
                  promo: Option[Promo] = None,
                  items: List[Item])

case class Promo(description: String, deduction: String)

case class Item(name: String,
                productCode: String,
                quantity: String,
                unitPrice: String,
                totalPrice: String,
                totalVat: String,
                total: String)

case class Tableware(napkins: Int, cups: Int, plates: Int)