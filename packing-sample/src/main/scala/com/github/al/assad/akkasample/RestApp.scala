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

  // init actor system
  implicit val system = ActorSystem(RootGuardian(), "rest-system")
  implicit val ec = system.executionContext
  implicit val sc = system.scheduler
  implicit val timeout: Timeout = 3.seconds
  sys.addShutdownHook(system.terminate())

  // init actors
  val counter = Await.result(system ? RootGuardian.GetCounterActor, 2.seconds)
  val scounter = Await.result(system ? RootGuardian.GetSingletonCounterActor, 2.seconds)

  // init root router
  val rootRoute =
    HelloService.route ~
    new CounterService(counter, scounter).route

  // launch http server
  Http().newServerAt("0.0.0.0", 8080).bind(rootRoute)
  logger.info("Server online at http://0.0.0.0:8080/")

}

object RootGuardian {
  sealed trait Command
  final case class GetCounterActor(replyTo: ActorRef[ActorRef[CounterActor.Command]]) extends Command
  final case class GetSingletonCounterActor(replyTo: ActorRef[ActorRef[CounterActorSingletonProxy.Command]]) extends Command

  def apply(): Behavior[Command] = Behaviors.setup { ctx =>
    ctx.log.info("Root guardian started.")
    val counter = ctx.spawn(CounterActor(), "counter")
    val scounter = ctx.spawn(CounterActorSingletonProxy(), "scounter")
    ctx.watch(counter)
    Behaviors.receiveMessage[Command] {
      case GetCounterActor(replyTo) =>
        replyTo ! counter
        Behaviors.same
      case GetSingletonCounterActor(replyTo) =>
        replyTo ! scounter
        Behaviors.same
    }
  }

}
