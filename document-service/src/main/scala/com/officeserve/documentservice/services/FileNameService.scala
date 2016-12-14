package com.officeserve.documentservice.services

import java.time.{Clock, LocalDate}
import java.time.format.DateTimeFormatter

trait FileNameService {
  /**
    * Generates the file name and path according to the current date
    *
    * @param value
    * @return
    */
  def generatePath(value: String): String

}

class FileNameServiceImpl(clock: Clock) extends FileNameService {

  private val formatter = DateTimeFormatter.ofPattern("YYYY/MM/dd")

  override def generatePath(value: String): String = {
    val now = LocalDate.now(clock)
    s"${now.format(formatter)}/${sanitise(value)}"

  }

  private def sanitise(fileName: String) = fileName.replaceAll("[ \\/]", "_")
}
