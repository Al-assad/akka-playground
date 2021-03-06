package com.github.al.assad.akka.Actor.a_hello

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}

/**
 * a simple ping-pong telling message example
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


object Ping2 {
  sealed trait Command
  final case object StartCmd extends Command
  final case class PongCmd(message: String) extends Command

  def apply(): Behavior[Command] = Behaviors.receive {
    case (context, StartCmd) =>
      // spawn Pong actor
      val pong = context.spawn(Pong2(), "pong")
      pong ! Pong2.PingCmd("scala", context.self)
      context.log.info(s"started Pong actor and send message complete")
      Behaviors.same
    case (context, PongCmd(message)) =>
      context.log.info(s"received pong message: $message")
      Behaviors.stopped
  }
}

object Pong2 {
  sealed trait Command
  final case class PingCmd(message: String, replyTo: ActorRef[Ping2.PongCmd]) extends Command

  def apply(): Behavior[Command] = Behaviors.receive {
    case (context, PingCmd(message, replyTo)) =>
      context.log.info(s"receive ping message: $message")
      replyTo ! Ping2.PongCmd(s"hello $message")
      Behaviors.stopped
  }
}

object PingPongMain2 {
  def main(args: Array[String]): Unit = {
    val system: ActorSystem[Ping2.Command] = ActorSystem(Ping2(), "ping-pong-sample")
    system ! Ping2.StartCmd
    system.terminate()
  }
}


