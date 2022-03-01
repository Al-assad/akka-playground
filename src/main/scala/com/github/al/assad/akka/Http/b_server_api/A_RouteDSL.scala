package com.github.al.assad.akka.Http.b_server_api

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes.NotFound
import akka.http.scaladsl.server.Directives.{parameter, _}
import akka.http.scaladsl.server.Route
import spray.json.DefaultJsonProtocol._

import scala.collection.mutable
import scala.language.postfixOps
import scala.util.{Failure, Success}

/** 演示各类 http dsl 使用
 *
 * https://doc.akka.io/docs/akka-http/current/routing-dsl/index.html
 */
//noinspection ScalaFileName
object DslSampleServer {

  implicit val system = ActorSystem(Behaviors.empty, "route-dsl-server")
  implicit val executionContext = system.executionContext

  final case class User(id: Long, name: String)
  final case class Greet(message: String, time: String)
  implicit val userFormat = jsonFormat2(User)
  implicit val greetFormat = jsonFormat2(Greet)

  val users = mutable.Map[Long, User]()

  // 根路由
  val rootRoute = concat(
    // 为子路由声明上层 path
    path("hello")(helloRoute),
    // 直接使用子路由提供的 path
    pingRoute,
    // 为一组子路由声明共同的上层 path
    pathPrefix("api") {
      pathPrefix("user")(userRoute) ~
      pathPrefix("file")(fileRoute) ~
      pathPrefix("query")(queryRoute)
    }
  )

  // "/hello" (all methods)
  def helloRoute: Route = _.complete("hi")

  // "/ping" (GET only)
  def pingRoute: Route =
    path("ping") {
      get {
        complete("pong")
      }
    }

  // pingRoute 的简练写法
  def pingRoute2: Route =
    path("ping")(get & complete("pong"))

  // "/api/users" 演示 User 资源的 Rest CURD
  def userRoute: Route = {
    pathEnd {
      post {
        entity(as[User]) { user =>
          users += (user.id -> user)
          complete("update user")
        }
      }
    } ~
    path(LongNumber) { id =>
      get {
        users.find(_._1 == id) match {
          case Some(user) => complete(user._2)
          case None => complete(NotFound)
        }
      } ~
      delete {
        users.remove(id)
        complete(s"delete user: $id")
      }
    } ~
    path("all") {
      get {
        complete(users.values.toList)
      }
    }
  }

  // 获取路由多个部分 "/api/file/*"
  def fileRoute: Route = path(Remaining) { filename =>
    get {
      complete(s"download: $filename")
    }
  }

  // 获取路由参数
  def queryRoute: Route = {
    get {
      pathPrefix("user") {
        pathEnd {
          // 可选参数 "/api/query/user?id=1&name=al"
          parameter("name".optional, "id".optional) {
            (name, id) =>
              var r = users.values
              if (id.isDefined) r = r.filter(_.id == id.get.toLong)
              if (name.isDefined) r = r.filter(_.name == name.get)
              complete(r)
          }
        } ~
        path("all") {
          // 必填参数 "/api/query/user/all?size=2"
          parameter("size".as[Int]) { size =>
            complete(users.values.take(size))
          }
        }
      }
    }
  }


  def main(args: Array[String]): Unit = {

    lazy val binding = Http().newServerAt("127.0.0.1", 8080).bind(rootRoute)
    binding.onComplete {
      case Success(b) =>
        // 启动成功处理
        println(
          s"Server online at http://127.0.0.1:8080/\nPress RETURN to stop..."
        )
        scala.io.StdIn.readLine()
        b.unbind()
      case Failure(e) =>
        // 启动失败处理
        println(s"Failed to bind to http://127.0.0.1:8080/\n${
          e.getMessage
        }")
        system.terminate()
    }
  }

}
