package officeserve.commons.spray
package auth

import com.typesafe.scalalogging.StrictLogging
import spray.http.HttpHeader
import spray.routing.AuthenticationFailedRejection
import spray.routing.authentication.ContextAuthenticator
import spray.routing.directives.SecurityDirectives

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class TrustedAuth extends SecurityDirectives with StrictLogging {

  import TrustedAuth._

  /** Spray doesn't parse custom headers automatically, so this does it for ours. */
  def parseCustomHdrs(hdrs: List[HttpHeader]): List[HttpHeader] = {
    val (cog, others) = hdrs.partition(_.name == `X-Cognito-Identity-Id`.name)
    `X-Cognito-Identity-Id`(cog.head.value, cog.tail.map(_.value):_*) +: others
  }

  val trustedHeaderAuthenticator: ContextAuthenticator[CognitoIdentityId] = { ctx =>
    Future {
      import AuthenticationFailedRejection._

      (for {
        trustedHeader <- ctx.request.mapHeaders(parseCustomHdrs).header[`X-Cognito-Identity-Id`]
        _ = logger.trace("trusted header found")
        cognitoIdentityId <- trustedHeader.optIdentityId
        _ = logger.info(s"Cognito identity ID: ${cognitoIdentityId}")
      } yield cognitoIdentityId) toRight {
        logger.error("Cognito header missing")
        AuthenticationFailedRejection(CredentialsMissing, List())
      }
    }
  }

  val auth = authenticate(trustedHeaderAuthenticator)

}

object TrustedAuth {

  type CognitoIdentityId = String

}
