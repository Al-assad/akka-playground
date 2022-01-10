package com.github.al.assad.akka.hello

import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors

/**
 * a simple ping-pong example
 */

/*
         ┌───Ping.Pong ◄──┐
         │                │
         ▼                │
  ┌──► Ping─────┐   ┌───►  Pong
  │             │   │
  │             │   │
  │             ▼   │
Start        Pong.Ping
 */

object Ping {
  sealed trait Command
  final case object StartCmd extends Command
  final case class PongCmd(message: String) extends Command

  def apply(): Behavior[Command] = Behaviors.receive {
    case (context, StartCmd) =>
      // spawn Pong actor
      val pong = context.spawn(Pong(), "pong")
      pong ! Pong.PingCmd("scala", context.self)
      context.log.info(s"started Pong actor and send message complete")
      Behaviors.same
    case (context, PongCmd(message)) =>
      context.log.info(s"received pong message: $message")
      Behaviors.stopped
  }
}

object Pong {
  sealed trait Command
  final case class PingCmd(message: String, replyTo: ActorRef[Ping.PongCmd]) extends Command

  def apply(): Behavior[Command] = Behaviors.receive {
    case (context, PingCmd(message, replyTo)) =>
      context.log.info(s"receive ping message: $message")
      replyTo ! Ping.PongCmd(s"hello $message")
      Behaviors.stopped
  }
}

object Main {
  def main(args: Array[String]): Unit = {
    val system: ActorSystem[Ping.Command] = ActorSystem(Ping(), "ping-pong-sample")
    system ! Ping.StartCmd
    system.terminate()
  }
}
