package com.github.al.assad.akkasample

import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.event.slf4j.Logger
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.RouteConcatenation
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
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

  // init root router
  val rootRoute =
    HelloService.route ~
    new CounterService(counter).route

  // launch http server
  Http().newServerAt("0.0.0.0", 8080).bind(rootRoute)
  logger.info("Http Server Started.")

  // launch akka management
  AkkaManagement(system).start()
  logger.info("Akka Management Started.")

  // launch cluster bootstrap process
  ClusterBootstrap(system).start()
  logger.info("Cluster Bootstrap Started.")
}

object RootGuardian {
  sealed trait Command
  final case class GetCounterActor(replyTo: ActorRef[ActorRef[_ >: CounterActor.Command]]) extends Command

  def apply(): Behavior[Command] = Behaviors.setup { ctx =>
    ctx.log.info("Root guardian started.")

    val counter = ctx.system.settings.config.getString("akka.actor.provider") match {
      case "cluster" =>
        ctx.log.info("Using cluster based counter actor.")
        ctx.spawn(CounterActorProxy(), "counter")
      case _ =>
        ctx.log.info("Using local based counter actor.")
        ctx.spawn(CounterActor(), "counter")
    }
    ctx.watch(counter)
    Behaviors.receiveMessage[Command] {
      case GetCounterActor(replyTo) =>
        replyTo ! counter
        Behaviors.same
    }
  }

}
