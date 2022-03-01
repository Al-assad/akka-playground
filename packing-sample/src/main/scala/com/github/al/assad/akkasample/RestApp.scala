package com.github.al.assad.akkasample

import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.event.slf4j.Logger
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.RouteConcatenation
import akka.util.Timeout

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt


object RestApp extends App with RouteConcatenation {
  val logger = Logger("RestApp")

  implicit val system = ActorSystem(RootGuardian(), "rest-system")
  sys.addShutdownHook(system.terminate())

  implicit val ec = system.executionContext
  implicit val sc = system.scheduler

  implicit val timeout: Timeout = 3.seconds
  val counter = Await.result(system ? RootGuardian.GetCounterActor, 2.seconds)

  val rootRoute =
    HelloService.route ~
    new CounterService(counter).route

  Http().newServerAt("0.0.0.0", 8080).bind(rootRoute)

  logger.info("Server online at http://0.0.0.0:8080/")

}

object RootGuardian {
  sealed trait Command
  final case class GetCounterActor(replyTo: ActorRef[ActorRef[CounterActor.Command]]) extends Command

  def apply(): Behavior[Command] = Behaviors.setup { ctx =>
    ctx.log.info("Root guardian started.")
    val counter = ctx.spawn(CounterActor(), "counter")
    Behaviors.receiveMessage {
      case GetCounterActor(replyTo) =>
        replyTo ! counter
        Behaviors.same
    }
  }

}
