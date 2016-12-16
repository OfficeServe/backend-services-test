package com.officeserve.sendemail.lambda.utils

import scala.concurrent.duration.{FiniteDuration, _}
import scala.concurrent.{Await, Future}

/**
  * Created by nicoofficeserve on 09/09/2016.
  */
trait FutureUtils {

  implicit def implicitAwait[T](f: Future[T])(implicit futureTimeout: FiniteDuration = 5 seconds): T =
    Await.result(f, futureTimeout)

}
