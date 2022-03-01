package com.github.al.assad.akkasample

import akka.http.scaladsl.server.{Directives, Route}

object HelloService extends Directives with DefaultJsonFormats {

  val route: Route =
    defaultRoute ~
    getHello ~
    getSayHelloWithName

  // get /
  def defaultRoute: Route = pathEndOrSingleSlash {
    get {
      complete("Hello, world!")
    }
  }

  // get/hello
  def getHello: Route = path("hello") {
    get {
      complete("Hi!")
    }
  }

  // sayHello/:name
  def getSayHelloWithName: Route =
    path("sayHello" / Segment) { name =>
      get {
        complete(s"Hello $name")
      }
    }

}


