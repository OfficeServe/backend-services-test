package officeserve.commons.spray.auth

import spray.http._

import TrustedAuth.CognitoIdentityId

object `X-Cognito-Identity-Id` extends Renderable {
  def apply(first: CognitoIdentityId, more: CognitoIdentityId*): `X-Cognito-Identity-Id` =
    apply(if(more.isEmpty) Some(first) else None) // multiple values indicate spoof attempt
  val name = "X-Cognito-Identity-Id"
  def render[R <: Rendering](r: R): r.type = r ~~ name ~~ ':' ~~ ' '
}

case class `X-Cognito-Identity-Id`(optIdentityId: Option[CognitoIdentityId]) extends HttpHeader {
  override def render[R <: Rendering](r: R): r.type = renderValue(r ~~ companion)
  def renderValue[R <: Rendering](r: R): r.type = r ~~ optIdentityId.get
  protected def companion = `X-Cognito-Identity-Id`
  override def name = companion.name
  override def lowercaseName = name.toLowerCase
  override def value: String = renderValue(new StringRendering).get
}
