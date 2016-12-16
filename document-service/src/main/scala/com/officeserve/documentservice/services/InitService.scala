package com.officeserve.documentservice.services

import akka.actor.ActorSystem
import akka.pattern.after
import com.officeserve.documentservice.services.S3Service.ServiceUnavailableException
import com.officeserve.documentservice.settings.AppSettings
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{ExecutionContext, Future}


trait InitService {
  /**
    * Performs all the needed initialisations
    *
    * @return
    */
  def initialise: Future[Unit]
}

class InitServiceImpl(settings: AppSettings, s3Service: S3Service)(implicit ec: ExecutionContext, system: ActorSystem) extends InitService with LazyLogging {

  override def initialise: Future[Unit] = {
    val createDocumentBucket = createBucket(settings.documentSettings.documentsBucket)
    val createTemplatesBucket = createBucket(settings.documentSettings.templatesBucket)
    for {
      _ <- createDocumentBucket
      _ <- createTemplatesBucket
    } yield ()
  }

  private[this] def createBucket(bucketName: String): Future[Unit] = {

    def retry(iteration: Int): Future[Unit] =
      s3Service.createBucket(bucketName)
        .recoverWith {
          case e: ServiceUnavailableException =>
            val retrySettings = settings.retrySettings.init
            if (iteration < retrySettings.maxTimes) {
              logger.error("Can't connect to S3, retrying", e)
              after(retrySettings.delay, system.scheduler) {
                retry(iteration + 1)
              }
            } else {
              logger.error("Can't connect to S3. Max number of retries reached. Giving up", e)
              Future.failed(e)
            }
        }


    retry(0)
  }
}
