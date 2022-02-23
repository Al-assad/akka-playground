package com.github.al.assad.akka.Http.a_quickstart

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives.{complete, get, path}

import scala.io.StdIn

/**
 * Akka-http HelloWorld sample
 */
object A_HelloWorld {

  def main(args: Array[String]): Unit = {
    // use implicit ActorSystem
    implicit val system = ActorSystem(Behaviors.empty, "hello-akka-http")
    // needed for the future flatMap/onComplete in the end
    implicit val executionContext = system.executionContext

    // define a route
    val route = path("hello") {
      get {
        complete("Hello World!")
      }
    }

    // bind route to http server
    val bindingFuture = Http().newServerAt("127.0.0.1", 8080).bind(route)

    // presses return key to terminate akka-http server
    println(
      s"""Server now online. Please navigate to http://127.0.0.1:8080/hello
         |Press RETURN to stop...""".stripMargin)
    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }
}
