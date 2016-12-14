package com.officeserve.documentservice.services

import java.io.InputStream
import java.net.URL

import com.amazonaws.AmazonClientException
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.{AmazonS3Exception, ObjectMetadata}
import com.officeserve.documentservice.settings.S3Settings
import org.apache.http.NoHttpResponseException
import org.apache.http.conn.HttpHostConnectException

import scala.concurrent.{ExecutionContext, Future, _}
import scala.util.control.NonFatal

trait S3Service {
  /**
    * Stores an object in a bucket and returns the public URL
    *
    * @param bucketName
    * @param fileName
    * @param file
    * @return
    */
  def putObject(bucketName: String, fileName: String, file: InputStream): Future[URL]

  /**
    * Retrieves an object from a bucket
    *
    * @param bucketName
    * @param fileName
    */
  def getObject(bucketName: String, fileName: String): Future[InputStream]

  /**
    * Creates a bucket
    *
    * @param bucketName
    * @return
    */
  def createBucket(bucketName: String): Future[Unit]
}

object S3Service {

  sealed trait S3Exception extends Exception

  case class NoSuchBucketException(message: String, cause: Throwable) extends Exception(message, cause) with S3Exception

  case class BucketAlreadyExistsException(message: String, cause: Throwable) extends Exception(message, cause) with S3Exception

  case class ObjectNotFoundException(message: String, cause: Throwable) extends Exception(message, cause) with S3Exception

  case class ServiceUnavailableException(message: String, cause: Throwable) extends Exception(message, cause) with S3Exception

  case class GenericS3Exception(message: String, cause: Throwable) extends Exception(message, cause) with S3Exception


  def exceptionMapping[U]: PartialFunction[Throwable, Future[U]] = {
    case x: AmazonS3Exception => Future.failed(fromAwsS3Exception(x))
    case x: AmazonClientException => Future.failed(fromAwsClientException(x))
    case NonFatal(x) => Future.failed(GenericS3Exception(x.getMessage, x))
  }

  private def fromAwsS3Exception(exception: AmazonS3Exception): S3Exception =
    exception.getErrorCode match {
      case "NoSuchBucket" => NoSuchBucketException(exception.getMessage, exception)
      case "BucketAlreadyOwnedByYou" => BucketAlreadyExistsException(exception.getMessage, exception)
      case "NoSuchKey" => ObjectNotFoundException(exception.getMessage, exception)
      case _ => GenericS3Exception(exception.getMessage, exception)
    }

  private def fromAwsClientException(exception: AmazonClientException): S3Exception =
    exception.getCause match {
      case x: HttpHostConnectException => ServiceUnavailableException(exception.getMessage, x)
      case x: NoHttpResponseException => ServiceUnavailableException(exception.getMessage, x)
      case NonFatal(x) => GenericS3Exception(x.getMessage, x)
    }

}

class S3ServiceImpl(settings: S3Settings)(implicit ec: ExecutionContext) extends S3Service {

  import S3Service._

  private val s3 = new AmazonS3Client()
  s3.setEndpoint(settings.endpoint)


  override def putObject(bucketName: String, fileName: String, input: InputStream): Future[URL] =
    Future {
      blocking {
        val metadata: ObjectMetadata = new ObjectMetadata()
        s3.putObject(bucketName, fileName, input, metadata)
        input.close()
        publicUrl(bucketName, fileName)
      }
    }.recoverWith(exceptionMapping)


  private def publicUrl(bucketName: String, key: String): URL =
    new URL(s"${settings.endpoint}/$bucketName/$key")

  override def createBucket(bucketName: String): Future[Unit] =
    Future(blocking(s3.createBucket(bucketName)))
      .map(_ => ())
      .recoverWith(exceptionMapping)


  override def getObject(bucketName: String, fileName: String): Future[InputStream] =
    Future(blocking(s3.getObject(bucketName, fileName).getObjectContent))
      .recoverWith(exceptionMapping)
}
