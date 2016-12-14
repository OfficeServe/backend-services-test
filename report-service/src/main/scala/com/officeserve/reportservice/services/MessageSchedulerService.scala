package com.officeserve.reportservice.services

import akka.actor.{ActorRef, ActorSystem}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._


trait MessageSchedulerService {
  def schedule(interval: FiniteDuration, receiver: ActorRef, message: Any): Unit
}

class MessageSchedulerServiceImpl(system: ActorSystem)(implicit ec: ExecutionContext) extends MessageSchedulerService {
  override def schedule(interval: FiniteDuration, receiver: ActorRef, message: Any): Unit =
    system.scheduler.schedule(0 seconds, interval, receiver, message)

}
