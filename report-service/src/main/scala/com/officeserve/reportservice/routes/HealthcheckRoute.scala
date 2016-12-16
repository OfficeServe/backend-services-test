package com.officeserve.reportservice.routes

import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{RequestContext, Route, RouteResult}

import scala.concurrent.{ExecutionContext, Future}

class HealthcheckRoute(implicit val ec: ExecutionContext) extends Route {
  override def apply(rc: RequestContext): Future[RouteResult] = {
    //TODO: improve this
    path("healthcheck") {
      get {
        complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, "ok"))
      }
    }.apply(rc)
  }
}
