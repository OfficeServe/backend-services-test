// Testing S3 is not our business, should not run on every change.

//package com.officeserve.documentservice.services
//
//import java.io.{ByteArrayInputStream, InputStream}
//import java.net.URL
//import java.nio.charset.StandardCharsets
//
//import com.amazonaws.services.s3.AmazonS3Client
//import com.officeserve.documentservice.services.S3Service.{BucketAlreadyExistsException, NoSuchBucketException, ObjectNotFoundException}
//import com.officeserve.documentservice.settings.{AppSettings, S3Settings}
//import com.officeserve.documentservice.utils.Retry
//import com.typesafe.config.ConfigFactory
//import org.scalatest.{AsyncFunSpec, Matchers}
//
//import scala.concurrent.duration._
//import scala.util.control.NonFatal
//import scala.util.{Random, Try}
//
//class S3ServiceTest extends AsyncFunSpec with Matchers {
//  val settings = new AppSettings(ConfigFactory.load()).s3Settings
//
//  val testingBucket = "testing-bucket"
//  val testingFile = "existing-file.txt"
//  val testingFileContent = "This is a test file"
//
//  init(settings, testingBucket, testingFile, testingFileContent)
//
//  describe("S3Service") {
//
//    val s3Service = new S3ServiceImpl(settings)
//
//    describe("when given a file to write") {
//      val is = new ByteArrayInputStream("Test content of a file".getBytes(StandardCharsets.UTF_8))
//      describe("in an existing bucket") {
//
//        it("should upload the file in S3 and eventually return the public URL") {
//          s3Service.putObject(testingBucket, "test-upload.txt", is).map { publicUrl =>
//            publicUrl shouldBe new URL(s"${settings.endpoint}/$testingBucket/test-upload.txt")
//          }
//        }
//      }
//      describe("in a non-existing bucket") {
//        it("should fail with an Exception") {
//          recoverToSucceededIf[NoSuchBucketException] {
//            s3Service.putObject("coolbucket", "test-upload.txt", is).map { publicUrl =>
//              publicUrl shouldBe new URL(s"${settings.endpoint}/$testingBucket/test-upload.txt")
//            }
//          }
//        }
//      }
//    }
//    describe("when creating a bucket") {
//      describe("that does not exist") {
//        it("should return a Success(true)") {
//          s3Service.createBucket("new-bucket" + Random.nextInt()).map(_ shouldBe())
//        }
//      }
//      describe("that already exists") {
//        it("should return a Success(false)") {
//          recoverToSucceededIf[BucketAlreadyExistsException] {
//            s3Service.createBucket(testingBucket)
//          }
//        }
//      }
//    }
//
//    describe("when retrieving a file") {
//      describe("that does not exist") {
//        it("should fail with an exception") {
//          recoverToSucceededIf[ObjectNotFoundException] {
//            s3Service.getObject(testingBucket, "non-existing-file.txt").map(_ should not be (null))
//          }
//        }
//      }
//      describe("that does exist") {
//        it("should return the file") {
//          s3Service.getObject(testingBucket, testingFile).map(streamToComparable(_) shouldBe testingFileContent)
//        }
//      }
//    }
//  }
//
//  def streamToComparable(is: InputStream): String =
//    new String(Stream.continually(is.read).takeWhile(_ != -1).map(_.toByte).toArray)
//
//
//  def init(settings: S3Settings, testingBucket: String, testingFileName: String, testingFileContent: String): Unit = {
//    val client = new AmazonS3Client()
//    client.setEndpoint(settings.endpoint)
//    //Waiting for the docker container to startup. I want it to fail if the container is not up
//    Retry(delay = 5 seconds, maxTimes = 5) {
//      client.listBuckets()
//    }.recover {
//      case NonFatal(e) => throw new RuntimeException("S3 bucket not available", e)
//    }
//
//    Try(client.createBucket(testingBucket)) //it will fail if the bucket already exists
//    client.putObject(testingBucket, testingFileName, testingFileContent)
//  }
//}
