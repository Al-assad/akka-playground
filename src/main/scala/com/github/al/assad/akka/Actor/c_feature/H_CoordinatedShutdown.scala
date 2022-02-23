package com.github.al.assad.akka.Actor.c_feature

import akka.Done
import akka.actor.CoordinatedShutdown
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import com.github.al.assad.akka.sleep

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

/**
 * Actor coordinated shutdown sample.
 *
 * https://doc.akka.io/docs/akka/current/coordinated-shutdown.html
 */
object CoordinatedShutdownSpec extends App {

  implicit val system = ActorSystem[Nothing](Behaviors.empty, "CoordinatedShutdownSpec1")

  // add a task at "before-service-unbind"
  CoordinatedShutdown(system).addTask(CoordinatedShutdown.PhaseBeforeServiceUnbind, "shutdown-task-1") { () =>
    println("@Message before-service-unbind run shutdown-task-1")
    Future.successful(Done)
  }

  sleep(5.seconds)
  println("@Message start to terminate system")
  system.terminate()
}
