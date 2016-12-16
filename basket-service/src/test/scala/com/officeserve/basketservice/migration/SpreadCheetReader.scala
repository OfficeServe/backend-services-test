package com.officeserve.basketservice.migration

import java.io.{File, FileInputStream}

import com.typesafe.scalalogging.StrictLogging
import officeserve.commons.spray.webutils.Error
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.xssf.usermodel.XSSFWorkbook

import scala.collection.JavaConversions._
import scala.concurrent.{Future, _}

/**
  * Created by mo on 15/11/2016.
  */

trait SpreadsheetReader {
  def read: Future[Either[Error, List[Row]]]

}

class SpreadsheetReaderImpl(filePath: String)(implicit ec: ExecutionContext) extends SpreadsheetReader
  with StrictLogging {

  def read: Future[Either[Error, List[Row]]] = Future {
    blocking {
      val file = new FileInputStream(new File(filePath))
      val wb = new XSSFWorkbook(file)
      val rows = wb.getSheetAt(0).iterator().toList.filterNot(_.getRowNum == 0)
      logger.info(s"processing ${rows.size} rows")
      Right(rows)
    }
  } recover {
    case e => Left(Error.withLogger(500, Some(e.getMessage), None, e))
  }

}


