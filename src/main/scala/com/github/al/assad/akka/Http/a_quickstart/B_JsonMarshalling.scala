package com.github.al.assad.akka.Http.a_quickstart

import akka.Done
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import spray.json.DefaultJsonProtocol._

import scala.concurrent.Future

/**
 * Json 序列化/反序列化示例
 */
object B_JsonMarshalling {

  implicit val system = ActorSystem(Behaviors.empty, "spray-example")
  implicit val executionContext = system.executionContext

  // domain model
  final case class Item(id: Long, name: String)
  final case class Order(items: List[Item])

  // formats for unmarshalling and marshalling
  implicit val itemFormat = jsonFormat2(Item)
  implicit val orderFormat = jsonFormat1(Order)

  // (fake) async database query api
  var orders: List[Item] = Nil

  def fetchItems(itemId: Long): Future[Option[Item]] = Future {
    orders.find(_.id == itemId)
  }

  def saveOrder(order: Order): Future[Done] = {
    orders = order match {
      case Order(items) => items ::: orders
      case _ => orders
    }
    Future.successful(Done)
  }


  // http server
  def main(args: Array[String]): Unit = {

    // route
    val route: Route =
      concat(
        // get Item
        get {
          pathPrefix("item" / LongNumber) { id =>
            val maybeItem: Future[Option[Item]] = fetchItems(id)
            onSuccess(maybeItem) {
              case Some(item) => complete(item)
              case None => complete(StatusCodes.NotFound)
            }
          }
        },
        // save order
        post {
          path("order") {
            // covert request body content to Order
            entity(as[Order]) { order =>
              val saved: Future[Done] = saveOrder(order)
              onComplete(saved) { _ =>
                complete("order created")
              }
            }
          }
        }
      )

    // start http-server
    Http().newServerAt("127.0.0.1", 8080).bind(route)
    println("akka-http server started.")
  }


}
