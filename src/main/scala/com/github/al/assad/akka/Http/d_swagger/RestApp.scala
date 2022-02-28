package com.github.al.assad.akka.Http.d_swagger

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.RouteConcatenation

// swagger: http://127.0.0.1:8080/api-docs/swagger.json
// swagger: http://127.0.0.1:8080/api-docs/swagger.yaml
object RestApp extends App with RouteConcatenation {

  implicit val system = ActorSystem(Behaviors.empty, "akka-http-sample")
  sys.addShutdownHook(system.terminate())

  implicit val executionContext = system.executionContext

  val routes =
    new EchoService().route ~
    new HelloService().route ~
    new SwaggerDocService().routes

  Http().newServerAt("127.0.0.1", 8080).bind(routes)
  println("akka-http server started.")

}





