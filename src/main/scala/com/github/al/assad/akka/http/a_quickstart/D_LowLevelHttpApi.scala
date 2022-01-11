package com.github.al.assad.akka.http.a_quickstart

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods.GET
import akka.http.scaladsl.model._

/**
 * Low-level HTTP API
 * https://doc.akka.io/docs/akka-http/current/introduction.html#low-level-http-server-apis
 *
 * 以上 A,B,C 示例使用的是 akka-http dsl 的 high-level api，akka-http 同样提供了 low-level api
 * 用于直接处理 HttpRequest、HttpResponse 对象
 */
object D_LowLevelHttpApi {

  def main(args: Array[String]): Unit = {

    implicit val system = ActorSystem(Behaviors.empty, "low-level")
    implicit val executionContext = system.executionContext

    // 手动定义 RequestHandler 路由
    val requestHandler: HttpRequest => HttpResponse = {

      case HttpRequest(GET, Uri.Path("/"), _, _, _) =>
        HttpResponse(entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          "<html><body>Hello world!</body></html>"))

      case HttpRequest(GET, Uri.Path("/ping"), _, _, _) =>
        HttpResponse(entity = "Pong!")

      case req: HttpRequest =>
        req.discardEntityBytes()
        HttpResponse(404, entity = "Unknown resource!")
    }

    Http().newServerAt("127.0.0.1", 8080).bindSync(requestHandler)
    println("akka-http server started.")

  }

}
