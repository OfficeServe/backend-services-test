package com.officeserve.documentservice

import java.time.Clock

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import com.github.mustachejava.DefaultMustacheFactory
import com.officeserve.documentservice.routes.{AllRoutes, DocumentRoute, HealthcheckRoute}
import com.officeserve.documentservice.services.S3Service.BucketAlreadyExistsException
import com.officeserve.documentservice.services._
import com.officeserve.documentservice.settings.AppSettings
import com.twitter.mustache.ScalaObjectHandler
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory

import scala.concurrent.Future

object Boot extends App {
  val logger = Logger(LoggerFactory.getLogger(Boot.getClass.getName))

  implicit val system = ActorSystem("document-system")
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  val settings = new AppSettings(ConfigFactory.load())

  lazy val s3Service = new S3ServiceImpl(settings.s3Settings)
  lazy val templateEngine = new DefaultMustacheFactory()
  templateEngine.setObjectHandler(new ScalaObjectHandler)
  lazy val pdfService = new PdfServiceImpl()
  lazy val fileNameService = new FileNameServiceImpl(Clock.systemUTC())
  lazy val documentService = new DocumentServiceImpl(settings.documentSettings, s3Service, templateEngine, pdfService, fileNameService)

  val documentRoute = new DocumentRoute(documentService)
  val sampleRoute = new HealthcheckRoute()
  val allRoutes = new AllRoutes(documentRoute, sampleRoute).all

  (for {
  // Intentionally sequential
    _ <- initialise
    bindings <- Http().bindAndHandle(allRoutes, settings.serverSettings.host, settings.serverSettings.port)
  } yield bindings)
    .map(bindings => logger.info(s"App started at ${settings.serverSettings.host}:${settings.serverSettings.port}"))
    .onFailure {
      case e =>
        logger.error("Cannot start server", e)
        system.terminate()
    }

  def initialise: Future[Unit] = {
    logger.info("Initialising...")
    new InitServiceImpl(settings, s3Service).initialise
      .recover {
        case e: BucketAlreadyExistsException => ()  // If the bucket already exists it's not an actual failure
      }
  }

}
