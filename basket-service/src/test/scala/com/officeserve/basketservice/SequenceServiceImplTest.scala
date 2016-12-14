package com.officeserve.basketservice

import com.officeserve.basketservice.service.SequenceServiceImpl
import com.officeserve.basketservice.settings.BasketSettings
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Span}
import org.scalatest.{FeatureSpec, GivenWhenThen, Matchers}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SequenceServiceImplTest extends FeatureSpec with Matchers with GivenWhenThen with ScalaFutures {

  val basketSettings = new BasketSettings()

  implicit val dbSettings = basketSettings.dynamoDBSettings

  implicit val patience = PatienceConfig(timeout = scaled(Span(20000, Millis)), interval = scaled(Span(100, Millis)))

  val seqId = "testSeq"
  val initialValue = 0
  val sequenceService = new SequenceServiceImpl().withSequenceIfNotExists(seqId, initialValue)

  info("Sequence generator test")
  scenario("Get next value") {
    Given(s"A sequence $seqId with initial value $initialValue")
    whenReady(sequenceService.saveOrUpdate(seqId, initialValue)) { _ =>
      When(s"I call nextValueFor($seqId)")
      whenReady(sequenceService.nextValueFor(seqId)) { v =>
        Then(s"The result should be ${initialValue + 1}")
        v shouldBe (initialValue + 1)
      }
    }
  }

  scenario("[Sequentially] Get next value executed many times") {
    Given(s"A sequence $seqId with initial value $initialValue")
    whenReady(sequenceService.saveOrUpdate(seqId, initialValue)) { _ =>
      When(s"I call nextValueFor($seqId) 4 times")
      val results: List[Long] = (for {
        v1 <- sequenceService.nextValueFor(seqId)
        v2 <- sequenceService.nextValueFor(seqId)
        v3 <- sequenceService.nextValueFor(seqId)
        v4 <- sequenceService.nextValueFor(seqId)
      } yield List(v1, v2, v3, v4)).futureValue
      Then(s"All values should be different")
      results shouldNot contain(-1)
      results.size shouldBe 4
      results shouldBe List(1, 2, 3, 4)
      results shouldBe results.sorted
    }
  }

  scenario("[In parallel] Get next value executed many times") {
    val seqId = "testSeq"
    val initialValue = 0
    Given(s"A sequence $seqId with initial value $initialValue")
    whenReady(sequenceService.saveOrUpdate(seqId, initialValue)) { _ =>
      val parallelism = 10
      When(s"I call nextValueFor($seqId) (in parallel) $parallelism times")
      val listOfFutures = (0 until parallelism).map { _ =>
        sequenceService.nextValueFor(seqId)
      }
      val results = Future.sequence(listOfFutures).futureValue
      Then(s"All values should be different")
      results shouldNot contain(-1)
      results.toSet.size shouldBe parallelism
    }
  }

}
