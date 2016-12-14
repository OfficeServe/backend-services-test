package com.officeserve.documentservice.services

import akka.actor.ActorSystem
import akka.testkit.TestKit
import com.officeserve.documentservice.settings.{AppSettings, DocumentSettings}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{AsyncFunSpec, AsyncFunSpecLike, BeforeAndAfterAll, Matchers}
import org.mockito.Mockito._
import org.mockito.ArgumentMatchers._

import scala.concurrent.Future

class InitServiceTest extends TestKit(ActorSystem("testSystem")) with AsyncFunSpecLike with Matchers with MockitoSugar with BeforeAndAfterAll {

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  describe("InitService") {

    val settings = mock[AppSettings]
    val s3Service = mock[S3Service]

    val documentSettings = DocumentSettings(
      templatesBucket = "bucket1",
      documentsBucket = "bucket2",
      invoicePdfTemplate = "invoice-pdf.mustache",
      receiptPdfTemplate = "receipt-pdf.mustache",
      deliveryNotePdfTemplate = "delivery_note-pdf.mustache",
      deliveryManifestPdfTemplate = "delivery_manifest-pdf.mustache",
      productReportPdfTemplate = "product_report-pdf.mustache",
      invoiceEmailTemplate = "invoice-email.mustache",
      receiptEmailTemplate = "receipt-email.mustache",
      cancellationEmailTemplate = "cancellation-email.mustache",
      deliveryNoteEmailTemplate = "delivery_note-email.mustache",
      productReportEmailTemplate = "product_report-email.mustache"
    )

    val initService = new InitServiceImpl(settings, s3Service)
    describe("when initialising") {
      when(settings.documentSettings) thenReturn documentSettings
      when(s3Service.createBucket(anyString())) thenReturn Future.successful(())
      it("should create the buckets specified in the configuration") {
        initService.initialise.map { x =>
          verify(s3Service, times(1)).createBucket("bucket1")
          verify(s3Service, times(1)).createBucket("bucket2")
          x shouldBe()
        }
      }
    }
  }

}
