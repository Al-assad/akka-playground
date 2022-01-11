package com.github.al.assad.akka.http.a_quickstart

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives.{complete, get, path}
import akka.stream.scaladsl.Source
import akka.util.ByteString

import scala.util.Random

/**
 * 数据流示例
 * client 请求 random 接口获取随机数据流，即使 client 为慢速处理，akka-http 也会会自动处理数据反压（backpressure）
 * https://doc.akka.io/docs/akka-http/current/introduction.html#streaming
 */
object C_StreamingData {

  def main(args: Array[String]): Unit = {

    implicit val system = ActorSystem(Behaviors.empty, "RandomNumbers")
    implicit val executionContext = system.executionContext

    // streams 是可重用的，所有的请求都会重用该 streams 产生的数据
    val numbers = Source.fromIterator(() => Iterator.continually(Random.nextInt()))

    val route =
      path("random") {
        get {
          complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, numbers.map(e => ByteString(s"$e\n"))))
        }
      }

    Http().newServerAt("127.0.0.1", 8080).bind(route)
    println("akka-http server started.")
  }

}
