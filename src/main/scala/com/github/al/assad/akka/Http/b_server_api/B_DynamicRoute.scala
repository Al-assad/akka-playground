package com.github.al.assad.akka.Http.b_server_api

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import spray.json.DefaultJsonProtocol._
import spray.json.JsValue
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

import scala.language.postfixOps

/**
 * 演示动态路由
 *
 * 以下演示创建一个支持动态创建路由的 mock 服务
 */
object B_DynamicRoute {

  case class MockDefinition(path: String, requests: Seq[JsValue], responses: Seq[JsValue])
  implicit val format = jsonFormat3(MockDefinition)

  @volatile var state = Map.empty[String, Map[JsValue, JsValue]]

  // fixed route to update state
  val fixedRoute: Route = post {
    path("mock") {
      entity(as[MockDefinition]) { mock =>
        val mapping = mock.requests.zip(mock.responses).toMap
        state = state + (mock.path -> mapping)
        complete("ok")
      }
    }
  }

  // dynamic routing based on current state
  val dynamicRoute: Route = ctx => {
    val routes = state.map {
      case (segment, responses) =>
        post {
          path(segment) {
            entity(as[JsValue]) { input =>
              complete(responses.get(input))
            }
          }
        }
    }
    concat(routes.toList: _*)(ctx)
  }

  val rootRoute: Route = fixedRoute ~ dynamicRoute


  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem(Behaviors.empty, "dynamic-route-dsl-server")
    implicit val executionContext = system.executionContext
    Http().newServerAt("127.0.0.1", 8080).bind(rootRoute)
  }

}
