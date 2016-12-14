import sbt._
import Keys._

object SpecialTesting {
  lazy val testAll = TaskKey[Unit]("test-all")

  private lazy val itSettings =
    inConfig(IntegrationTest)(Defaults.testSettings) ++
      Seq(
        fork in IntegrationTest := false,
        parallelExecution in IntegrationTest := false,
        scalaSource in IntegrationTest := baseDirectory.value / "src/advancedtesting/it")

  private lazy val e2eSettings =
    inConfig(SpecialConfigs.EndToEndTest)(Defaults.testSettings) ++
      Seq(
        fork in SpecialConfigs.EndToEndTest := false,
        parallelExecution in SpecialConfigs.EndToEndTest := false,
        scalaSource in SpecialConfigs.EndToEndTest := baseDirectory.value / "src/advancedtesting/end2end")



  lazy val settings = itSettings ++ e2eSettings ++ Seq(
    testAll <<= (test in SpecialConfigs.EndToEndTest ).dependsOn((test in IntegrationTest).dependsOn(test in Test))

  )
}
