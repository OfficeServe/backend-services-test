package com.officeserve.documentservice.services

import java.time.{Clock, Instant, ZoneId}

import org.scalatest.{Matchers, path}

class FileNameServiceTest extends path.FunSpec with Matchers {

  describe("FileNameService") {
    val clock = Clock.fixed(Instant.ofEpochMilli(0L), ZoneId.systemDefault())
    val fileNameService = new FileNameServiceImpl(clock)
    describe("when generating a path") {
      describe("given a filename without special character") {
        it("should concatenate the given filename with a path in the format YYYY/MM/dd/<filename>") {
          fileNameService.generatePath("test.pdf") shouldBe "1970/01/01/test.pdf"
        }
      }
      describe("given a filename containing special characters") {
        it("should concatenate the given filename with a path in the format YYYY/MM/dd/<filename> replacing") {
          fileNameService.generatePath("10/12/2016 test.pdf") shouldBe "1970/01/01/10_12_2016_test.pdf"
        }
      }
    }
  }

}
