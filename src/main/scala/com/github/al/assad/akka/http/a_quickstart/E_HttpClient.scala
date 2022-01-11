package com.github.al.assad.akka.http.a_quickstart

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}

import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
 * Http client example
 * https://doc.akka.io/docs/akka-http/current/introduction.html#http-client-api
 *
 * akka-http 提供了一套和 akka-http 使用相同 HttpRequest、HttpResponse 的客户端 api，
 * 但是增加了连接池复用能力
 */
object E_HttpClient {

  def main(args: Array[String]): Unit = {

    implicit val system = ActorSystem(Behaviors.empty, "client-request")
    implicit val executionContext = system.executionContext


    val resFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(uri = "http://127.0.0.1:8080/hello"))
    resFuture onComplete {
      case Success(res) => println(res)
      case Failure(exception) => println(exception)
    }
  }

}
