package com.officeserve.basketservice.service

import java.util.concurrent.Executors

import com.officeserve.basketservice.common.DynamoDBSettings
import com.officeserve.basketservice.persistence.{Sequence, SequenceRepository, SequenceRepositoryImpl}
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.blocking
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try


trait SequenceService {

  def nextValueFor(sequenceName: String): Future[Long]

  def saveOrUpdate(sequenceId: String, initialValue: Long): Future[Unit]
}

class SequenceServiceImpl(implicit settings: DynamoDBSettings) extends SequenceService with StrictLogging {

  implicit val ec = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(2))

  private val repo: SequenceRepository = new SequenceRepositoryImpl()

  private def nextValueFor(sequenceName: String, maxAttempts: Int): Future[Long] =
    repo.nextValueFor(sequenceName) recoverWith {
      case e => if (maxAttempts > 0) {
        val nextAttempt = maxAttempts - 1
        logger.warn(s"Attempt to get sequence '$sequenceName' failed, trying again. Attempts left: $nextAttempt")
        Future {
          blocking {
            Thread.sleep(500)
          }
        } flatMap { _ =>
          nextValueFor(sequenceName, nextAttempt)
        }
      } else {
        logger.error("Error getting sequence value", e)
        Future.failed(e)
      }
    }

  override def nextValueFor(sequenceName: String): Future[Long] =
    nextValueFor(sequenceName, 5)

  override def saveOrUpdate(sequenceId: String, initialValue: Long): Future[Unit] =
    repo.saveOrUpdate(Sequence(sequenceId, initialValue))

  def withSequenceIfNotExists(seqId: String, initialValue: Long = 0): this.type = {
    Try(repo.createIfNotExists(Sequence(seqId, initialValue)))
      .toOption.fold(logger.info(s"Sequence '$seqId' already exists")) { i =>
      logger.info(s"Sequence '$seqId' created and initialised to '$initialValue'")
    }
    this
  }

}
