package com.github.al.assad.akka.Http.c_other

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods.GET
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import spray.json.DefaultJsonProtocol._
import spray.json._

//noinspection DuplicatedCode
object FlinkProxyHttpMixApp extends App {

  implicit val system = ActorSystem(Behaviors.empty, "Proxy")
  implicit val ec = system.executionContext

  // flink rest address
  val flinkRests = List(
    ("hs.assad.site", 32241),
    ("hs.assad.site", 30527)
  )

  case class FlinkRestErrMock(errors: List[String])
  implicit val flinkRestErrMock = jsonFormat1(FlinkRestErrMock)

  // dynamic proxy route
  val proxy: Route = ctx => {
    val routes: List[Route] = flinkRests.map { case (host, port) => pathPrefix(s"$host:$port") {
      path(Remaining) { path => { ctx =>
        val request = ctx.request

        validateRequest(request) match {
          case (false, reason) =>
            ctx.complete(HttpResponse(400, entity = FlinkRestErrMock(List(s"Blocked requests due to $reason")).toJson.toString))
          case (true, _) =>
            val rpath = if (path.nonEmpty && !path.startsWith("/")) "/".concat(path) else path
            val proxyUri = request.uri
              .withScheme("http").withHost(host).withPort(port)
              .withPath(Path(rpath))
            val proxyReq = request.withUri(proxyUri)
              .withHeaders(request.headers.filter(_.name() != "Timeout-Access"))
            // println(s"proxy:[${request.method.name}] ${request.uri} => $proxyUri")
            ctx.complete(Http().singleRequest(proxyReq))
        }
      }
      }
    }
    }
    concat(routes: _*)(ctx)
  }

  // block modify operation route
  def validateRequest(request: HttpRequest): (Boolean, String) = {
    if (request.method != GET) false -> s"${request.method.name} method"
    else if (request.uri.path.toString.contains("yarn-cancel")) false -> "Cancel job api"
    else true -> ""
  }

  val root = concat(
    path("")(get &
      complete(s"try to route: \n${flinkRests.map(e => s"/proxy/${e._1}:${e._2}").mkString("\n")}")),
    pathPrefix("proxy")(proxy)
  )


  Http().newServerAt("127.0.0.1", 8080).bind(root)
  // todo add a standalone port

}
