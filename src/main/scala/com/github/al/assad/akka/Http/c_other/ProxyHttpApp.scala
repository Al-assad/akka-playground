package com.github.al.assad.akka.Http.c_other

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}

import scala.concurrent.Future

object ProxyHttpApp extends App {

  implicit val system = ActorSystem(Behaviors.empty, "Proxy")
  implicit val ec = system.executionContext

  val proxy: HttpRequest => Future[HttpResponse] = { request =>
    val proxyUri = request.uri.withHost("hs.assad.site").withPort(32241).withScheme("http")
    val proxyReq = request
      .withUri(proxyUri)
      .withHeaders(request.headers.filter(_.name() != "Timeout-Access"))

    println(s"proxy: ${request.uri} => ${proxyUri}")
    Http().singleRequest(proxyReq)
  }

  Http().newServerAt("127.0.0.1", 8080).bind(proxy)

}
