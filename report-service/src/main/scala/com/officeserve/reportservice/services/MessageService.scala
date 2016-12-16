package com.officeserve.reportservice.services

import com.officeserve.reportservice.settings.ReportSettings
import com.redis.{RedisClient, RedisClientPool, Seconds}

import scala.concurrent.{ExecutionContext, Future}

trait MessageService {

  import MessageStatus._

  def pending(messageId: String): Future[Boolean]

  def processed(messageId: String): Future[Boolean]

  def status(messageId: String): Future[Option[Status]]


  object MessageStatus {

    sealed abstract class Status(val name: String) {
      override def toString: String = name

    }

    case object Pending extends Status("PENDING")

    case object Processed extends Status("PROCESSED")

    def fromString(name: String): Option[Status] =
      name match {
        case Pending.name => Some(Pending)
        case Processed.name => Some(Processed)
        case _ => None
      }

  }

}


class MessageServiceImpl(settings: ReportSettings, redisClients: RedisClientPool)(implicit ec: ExecutionContext) extends MessageService {

  import MessageStatus._

  private def redisKey(messageId: String) = s"msg.$messageId.status"

  override def pending(messageId: String): Future[Boolean] = {
    val key = redisKey(messageId)
    asyncRedisOp(redisClients)(
      _.set(key, Pending.toString, onlyIfExists = false, Seconds(settings.pendingMessageTTL.toSeconds))
    )
  }


  override def processed(messageId: String): Future[Boolean] = {
    val key = redisKey(messageId)
    asyncRedisOp(redisClients)(
      _.setex(key, settings.processedMessageTTL.toSeconds, Processed.toString)
    )
  }

  override def status(messageId: String): Future[Option[Status]] = {
    val key = redisKey(messageId)
    asyncRedisOp(redisClients)(
      _.get(key).flatMap(k => MessageStatus.fromString(k))
    )
  }


  private def asyncRedisOp[T](redisClientPool: RedisClientPool)(body: RedisClient => T): Future[T] = {
    Future {
      redisClientPool.withClient { redis =>
        body(redis)
      }
    }
  }

}