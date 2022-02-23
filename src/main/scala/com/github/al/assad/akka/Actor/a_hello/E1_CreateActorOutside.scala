package com.github.al.assad.akka.Actor.a_hello

import akka.actor.typed._
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}

/**
 * Create actor from outside.
 * https://doc.akka.io/docs/akka/current/typed/actor-lifecycle.html#spawnprotocol
 *
 * Akka Typed no longer allows the creation of an actor through an instance of ActorSystem.
 * Child actors can then be started from the outside by telling or asking SpawnProtocol.
 * Spawn to the actor reference of the system.
 */

object UseSpawnProtocolSample extends App {

  // use Akka's build-in SpawnProtocol as AkkaSystem to initialize the Behavior
  implicit val system: ActorSystem[SpawnProtocol.Command] = ActorSystem(SpawnProtocol(), "spawn-protocol")
  implicit val ec: ExecutionContext = system.executionContext
  implicit val timeout: Timeout = 2.seconds

  // spawn Ping2 Actor
  val pingFuture: Future[ActorRef[Ping2.Command]] = system.ask(SpawnProtocol.Spawn(behavior = Ping2(), name = "ping", props = Props.empty, _))
  //  val pingFuture = system.ask[ActorRef[Ping2.Command]](replyTo => SpawnProtocol.Spawn(Ping2(), "ping", Props.empty, replyTo))
  val ping: ActorRef[Ping2.Command] = Await.result(pingFuture, 2.seconds)

  ping ! Ping2.StartCmd
  system.terminate()
}

