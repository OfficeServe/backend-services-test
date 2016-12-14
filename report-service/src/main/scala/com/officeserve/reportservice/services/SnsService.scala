package com.officeserve.reportservice.services

import com.amazonaws.handlers.AsyncHandler
import com.amazonaws.services.sns.AmazonSNSAsyncClient
import com.amazonaws.services.sns.model.{PublishRequest, PublishResult}
import com.officeserve.reportservice.services.SnsService.MessageId
import com.officeserve.reportservice.settings.SnsSettings

import scala.concurrent.{Future, Promise}

trait SnsService {


  /**
    * Publishes a message into SNS
    *
    * @param topicArn
    * @param message
    * @return the messageId
    */
  def publish(topicArn: String, message: String): Future[MessageId]

}

object SnsService {
  type MessageId = String

  def apply(snsSettings: SnsSettings): SnsService = {
    new SnsServiceImpl(new AmazonSNSAsyncClient().withEndpoint(snsSettings.endpoint))
  }

  def apply(amazonSNSAsyncClient: AmazonSNSAsyncClient): SnsService =
    new SnsServiceImpl(amazonSNSAsyncClient)
}


class SnsServiceImpl(snsClient: AmazonSNSAsyncClient) extends SnsService {
  override def publish(topicArn: String, message: String): Future[MessageId] = {

    val p = Promise[MessageId]

    snsClient.publishAsync(topicArn, message, new AsyncHandler[PublishRequest, PublishResult] {
      override def onError(exception: Exception): Unit =
        p.failure(exception)

      override def onSuccess(request: PublishRequest, result: PublishResult): Unit =
        p.success(result.getMessageId)

    })

    p.future
  }
}