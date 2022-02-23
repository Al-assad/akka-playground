package com.github.al.assad.akka.Http.b_server_api

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.StatusCodes.InternalServerError
import akka.http.scaladsl.server.{ExceptionHandler, Route}
import akka.http.scaladsl.server.Directives._

/**
 * 路由异常处理
 */
object C_ErrorHandler {


  implicit def myExceptionHandler: ExceptionHandler =
    ExceptionHandler {
      case _: ArithmeticException =>
        extractUri { uri =>
          println(s"Request to $uri could not be handled normally")
          complete(HttpResponse(InternalServerError, entity = "Bad numbers, bad result!!!"))
        }
    }

  val route: Route = Route.seal(
    path("divide") {
      complete((1 / 0).toString) //Will throw ArithmeticException
    }
  )

  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem(Behaviors.empty, "route-error-handle-server")
    implicit val executionContext = system.executionContext
    Http().newServerAt("127.0.0.1", 8080).bind(route)
  }
}
