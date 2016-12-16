package com.officeserve.documentservice.utils

import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success, Try}


object Retry {
  def apply[T](delay: FiniteDuration, maxTimes: Int)(body: => T): Try[T] = {
    Try(body) match {
      case s@Success(_) => s
      case Failure(_) if maxTimes > 0 =>
        Thread.sleep(delay.toMillis)
        Retry(delay, maxTimes - 1)(body)
      case f@Failure(_) => f
    }
  }
}
