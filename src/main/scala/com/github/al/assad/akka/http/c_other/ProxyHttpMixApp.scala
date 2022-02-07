package com.github.al.assad.akka.http.c_other

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

//noinspection DuplicatedCode
object ProxyHttpMixApp extends App {

  implicit val system = ActorSystem(Behaviors.empty, "Proxy")
  implicit val ec = system.executionContext

  // flink rest address
  val flinkRests = List(
    ("hs.assad.site", 32241),
    ("hs.assad.site", 30527)
  )

  // dynamic proxy route
  val proxy: Route = ctx => {
    val routes: List[Route] = flinkRests.map {
      case (host, port) => pathPrefix(s"$host:$port") {
        path(Remaining) { path => { ctx =>
          val request = ctx.request
          val rpath = if (path.nonEmpty && !path.startsWith("/")) "/".concat(path) else path

          val proxyUri = request.uri
            .withScheme("http")
            .withHost(host)
            .withPort(port)
            .withPath(Path(rpath))

          val proxyReq = request
            .withUri(proxyUri)
            .withHeaders(request.headers.filter(_.name() != "Timeout-Access"))

          println(s"proxy: ${request.uri} => $proxyUri")
          ctx.complete(Http().singleRequest(proxyReq))
        }
        }
      }
    }
    concat(routes: _*)(ctx)
  }

  // todo block not GET method and cancel api

  val root = concat(
    path("")(get &
      complete(s"try to route: \n${flinkRests.map(e => s"/proxy/${e._1}:${e._2}").mkString("\n")}")),
    pathPrefix("proxy")(proxy)
  )


  Http().newServerAt("127.0.0.1", 8080).bind(root)

}
