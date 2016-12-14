package com.officeserve.basketservice.migration

import com.officeserve.basketservice.persistence.{Address, InvoiceAddress}
import com.officeserve.basketservice.service.{OnAccountUser, PaymentMethodServiceAdmin}
import com.typesafe.scalalogging.StrictLogging
import officeserve.commons.spray.webutils.Error
import org.apache.poi.ss.usermodel.Row

import scala.concurrent.{ExecutionContext, Future}

/**
  * Created by mo on 15/11/2016.
  */
class OnAccountPaymentMethodMigration(paymentMethodServiceAdmin: PaymentMethodServiceAdmin, spreadsheetReader: SpreadsheetReader)(implicit ec: ExecutionContext)
  extends StrictLogging {

  import OnAccountCellMapping._

  def migrateOnAccountUserFromSpreadSheet: Future[Either[Error, Unit]] =
    spreadsheetReader.read.flatMap {
      case Right(rows) => paymentMethodServiceAdmin.giveOnAccountPrivilege(rows.foldLeft(Set[OnAccountUser]())((acc, curr) => acc + toOnAccountUser(curr)))
      case Left(e) => Future.successful(Left(e))
    }

  private def toOnAccountUser(r: Row): OnAccountUser = {
    val invoiceAddress = InvoiceAddress(fullName = s"${trim(FIRST_NAME, r)} ${trim(SURNAME_NAME, r)} ",
      address = Address(addressLine1 = s"${trim(COMPANY, r)}, ${trim(ADDRESS_LINE_1, r)}",
        addressLine2 = Some(trim(ADDRESS_LINE_1, r)),
        postCode = trim(POSTCODE, r),
        city = trim(CITY, r)))

    OnAccountUser(email = trim(EMAIL, r), address = invoiceAddress)
  }
}

object OnAccountCellMapping {

  def trim(index: Int, r: Row): String = r.getCell(index).toString.trim

  val FIRST_NAME = 0
  val SURNAME_NAME = 1
  val EMAIL = 2
  val COMPANY = 3
  val ADDRESS_LINE_1 = 4
  val ADDRESS_LINE_2 = 5
  val CITY = 6
  val POSTCODE = 7
}