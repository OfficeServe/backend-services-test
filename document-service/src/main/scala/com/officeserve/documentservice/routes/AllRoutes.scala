package com.officeserve.documentservice.routes

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._


class AllRoutes(unsafe: Route*) {
  def all: Route =
    unsafe.reduceLeft { (acc, curr) => acc ~ curr }
}
