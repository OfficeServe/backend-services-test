package com.officeserve.reportservice

import java.time.Clock

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import com.amazonaws.services.sqs.AmazonSQSClient
import com.officeserve.documentservice.api.DocumentServiceApi
import com.officeserve.reportservice.actors.{SqsActor, SqsConsumer, SqsIdempotentConsumer}
import com.officeserve.reportservice.repositories.{OrderLogRepository, ProductReportLogRepository}
import com.officeserve.reportservice.routes.{AllRoutes, HealthcheckRoute}
import com.officeserve.reportservice.services._
import com.officeserve.reportservice.settings.AppSettings
import com.redis.RedisClientPool
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.Future

object Boot extends App with LazyLogging {

  implicit val ec = scala.concurrent.ExecutionContext.Implicits.global

  val settings = new AppSettings(ConfigFactory.load())

  val system = ActorSystem("report-system-app")

  lazy val sqsClient = new AmazonSQSClient()

  lazy val snsService = SnsService(settings.snsSettings)

  lazy val redis = new RedisClientPool(settings.redisSettings.host, settings.redisSettings.port)

  lazy val messageService = new MessageServiceImpl(settings.reportSettings, redis)

  lazy val messageSchedulerService = new MessageSchedulerServiceImpl(system)

  lazy val messageTranslatorService = new MessageTranslatorServiceImpl()

  lazy val documentServiceApi = DocumentServiceApi()

  lazy val orderLogRepository = OrderLogRepository(settings.dynamoDBSettings)

  lazy val productReportLogRepository = ProductReportLogRepository(settings.dynamoDBSettings)

  val clock = Clock.systemDefaultZone()

  lazy val productReportService = new ProductReportServiceImpl(clock, orderLogRepository, productReportLogRepository)

  lazy val messageConsumerService = new MessageConsumerServiceImpl(settings, documentServiceApi, messageTranslatorService, snsService, productReportService)


  lazy val consumerFactory = SqsConsumer.factory(settings, messageSchedulerService, messageTranslatorService, messageConsumerService)

  lazy val idempotentConsumerFactory = SqsIdempotentConsumer.factory(messageService, settings, consumerFactory)


  val sqsActor = SqsActor.factory(sqsClient, settings, idempotentConsumerFactory)(ec)(system)


  WebBoot.boot(settings)
    .map(_ => logger.info("App started"))
    .onFailure {
      case e =>
        logger.error("Cannot start server", e)
        shutdown
    }

  sys.addShutdownHook {
    shutdown
  }

  private def shutdown = {
    documentServiceApi.close()
    system.terminate()
      .map(_ => logger.info("App stopped"))
    WebBoot.terminate
      .map(_ => logger.info("Web stopped"))
  }
}

object WebBoot extends LazyLogging {

  private implicit val webSystem = ActorSystem("report-system-web")
  private implicit val materializer = ActorMaterializer()

  private implicit val ec = webSystem.dispatcher

  private val healthCheck = new HealthcheckRoute()
  private val allRoutes = new AllRoutes(healthCheck).all


  def boot(settings: AppSettings): Future[Http.ServerBinding] =
    Http(webSystem).bindAndHandle(allRoutes, settings.serverSettings.host, settings.serverSettings.port)
      .map { binding =>
        logger.info(s"WebApp started at ${settings.serverSettings.host}:${settings.serverSettings.port}")
        binding
      }

  def terminate = webSystem.terminate()
}
