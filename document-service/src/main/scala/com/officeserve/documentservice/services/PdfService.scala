package com.officeserve.documentservice.services

import java.io._

import com.officeserve.documentservice.services.PdfService.{Landscape, Orientation, Portrait}
import io.github.cloudify.scala.spdf.{Pdf, PdfConfig, SourceDocumentLike, Landscape => SPDFLandscape, Portrait => SPDFPortrait}

import scala.concurrent.{ExecutionContext, Future}

trait PdfService {

  /**
    * Converts the given inputStream containing HTML into a PDF
    *
    * @param inputStream
    * @return
    */
  def generate(inputStream: InputStream, orientation: Orientation = Portrait): InputStream
}

object PdfService {

  sealed trait Orientation

  case object Portrait extends Orientation

  case object Landscape extends Orientation

}

/**
  * Converts HTML to PDF using wkhtmltopdf
  */
class PdfServiceImpl(implicit ec: ExecutionContext) extends PdfService {

  private val pdfPortrait = Pdf(new PdfConfig {
    orientation := SPDFPortrait
    pageSize := "A4"
    marginTop := "0mm"
    marginBottom := "0mm"
    marginLeft := "0mm"
    marginRight := "0mm"
  })
  private val pdfLandscape = Pdf(new PdfConfig {
    orientation := SPDFLandscape
    pageSize := "A4"
    marginTop := "0mm"
    marginBottom := "0mm"
    marginLeft := "0mm"
    marginRight := "0mm"
  })


  override def generate(inputStream: InputStream, orientation: Orientation = Portrait ): InputStream =
    pipeline(inputStream, orientation)


  private def pipeline[T: SourceDocumentLike](input: => T, orientation: Orientation): InputStream = {
    val pipedOutputStream = new PipedOutputStream()
    val pipedInputStream = new PipedInputStream(pipedOutputStream)

    // Reader and writer of the piped stream should be on separate threads to avoid deadlocks
    Future {
      (orientation match {
        case Portrait => pdfPortrait
        case Landscape => pdfLandscape
      }).run(input, pipedOutputStream)
    }.onComplete(_ => pipedOutputStream.close())

    pipedInputStream
  }
}
