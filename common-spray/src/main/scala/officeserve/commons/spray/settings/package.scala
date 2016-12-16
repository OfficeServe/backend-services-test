package officeserve.commons.spray.settings

import scala.concurrent.duration.FiniteDuration

case class ServerSettings(host: String, port: Int, timeout: FiniteDuration)