package com.officeserve.basketservice.migration

import com.officeserve.basketservice.persistence.{Covered, PostCode, PostCodeRepository, UpComing}
import com.typesafe.scalalogging.StrictLogging
import officeserve.commons.spray.webutils.Error
import org.apache.poi.ss.usermodel.Row

import scala.concurrent.{ExecutionContext, Future}

/**
  * Created by mo on 15/11/2016.
  */
class PostCodeMigration(postCodeRepository: PostCodeRepository, spreadsheetReader: SpreadsheetReader)(implicit ec: ExecutionContext)
  extends StrictLogging {

  import PostCodeCellMapping._

  def migratePostCodeFromSpreadsheet: Future[Either[Error, Unit]] =
    spreadsheetReader.read.flatMap {
      case Right(rows) => postCodeRepository.savePostCodes(rows.foldLeft(Set[PostCode]())((acc, curr) => acc + toPostCode(curr)))
      case Left(e) => Future.successful(Left(e))
    }

  private def toPostCode(r: Row): PostCode = {
    val courier = trim(COURIER, r) match {
      case "NONE" => None
      case c: String => Some(c)
    }

    val coverageStatus = trim(COVERAGE_STATUS, r) match {
      case "Covered" => Covered
      case "UpComing" => UpComing
      case c: String => throw new Exception(s"Do not recognise $c coverageStatus")
    }


    PostCode(trim(AREA, r),
      trim(AREA_NAME, r),
      coverageStatus,
      courier,
      trim(IS_LONDON_AREA, r).toBoolean)
  }
}

object PostCodeCellMapping {

  def trim(index: Int, r: Row): String = r.getCell(index).toString.trim

  val AREA = 0
  val AREA_NAME = 1
  val COVERAGE_STATUS = 2
  val COURIER = 3
  val IS_LONDON_AREA = 4
}