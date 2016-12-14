import sbt._

object SpecialConfigs {
  val IntegrationTest = config("it") extend (Runtime)
  val EndToEndTest = config("e2e") extend (Runtime)
  val all = Seq(IntegrationTest, EndToEndTest)
}
