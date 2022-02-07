package com.github.al.assad.akka.http.c_other

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}

import scala.concurrent.Future

object ProxyHttpApp extends App {

  implicit val system = ActorSystem(Behaviors.empty, "Proxy")
  implicit val ec = system.executionContext

  val proxy: HttpRequest => Future[HttpResponse] = { request =>
    val proxyUri = "http://hs.assad.site:32241" + request.uri.path.toString
    val proxyReq = request
      .withUri(proxyUri)
      .withHeaders(request.headers.filter(_.name() != "Timeout-Access"))

    println(s"proxy: ${request.uri} => ${proxyUri}")

    Http().singleRequest(proxyReq)
  }


  Http().newServerAt("127.0.0.1", 8080).bind(proxy)


}
