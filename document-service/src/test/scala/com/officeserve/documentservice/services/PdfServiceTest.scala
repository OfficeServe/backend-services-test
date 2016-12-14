package com.officeserve.documentservice.services

import java.io.{ByteArrayInputStream, InputStream}

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.scalatest.{Matchers, path}

import scala.concurrent.ExecutionContext.Implicits.global

class PdfServiceTest extends path.FunSpec with Matchers {

  // describe("PdfService") {
  //   val pdfService = new PdfServiceImpl
  //   describe("when given an html") {
  //     val html = "<html><body>hello world!</body></html>"
  //     describe("as an InputStream") {
  //       val is = pdfService.generate(new ByteArrayInputStream(html.getBytes))
  //       itShouldBeTheExpectedPDF(is)
  //     }
  //   }
  // }
  //
  // def itShouldBeTheExpectedPDF(is: InputStream): Unit = {
  //   val document = PDDocument.load(is)
  //
  //   val content = new PDFTextStripper().getText(document)
  //   document.close()
  //   content should include("hello")
  //   content should include("world!")
  // }
}
