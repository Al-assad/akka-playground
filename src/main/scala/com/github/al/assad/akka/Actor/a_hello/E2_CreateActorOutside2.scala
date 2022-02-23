package com.github.al.assad.akka.Actor.a_hello

import akka.actor.typed._
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.language.implicitConversions
import scala.util.{Failure, Success}

/**
 * request-response with ask from outside
 */
//noinspection DuplicatedCode

object ActorAskOutsideSample {


  object PingBot {
    sealed trait Command
    final case class Ping(msg: String, replyTo: ActorRef[Reply]) extends Command

    sealed trait Reply
    final case class Pong(msg: String) extends Reply

    def apply(): Behavior[Command] = Behaviors.receiveMessage {
      case Ping(msg, replyTo) =>
        replyTo ! Pong(s"bot reply: $msg")
        Behaviors.same
    }
  }


  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem(PingBot(), "ping-bot")
    implicit val timeout: Timeout = 3.seconds
    implicit val ec = system.executionContext

    val result: Future[PingBot.Reply] = system.ask(ref => PingBot.Ping("hello", ref))
    result.onComplete {
      case Success(PingBot.Pong(msg)) => println(s"received: $msg")
      case Failure(ex) => println(s"failed: $ex")
    }
    println("continue")
    system.terminate()
  }

  def main2(args: Array[String]): Unit = {
    implicit val system = ActorSystem(PingBot(), "ping-bot")
    implicit val timeout: Timeout = 3.seconds
    implicit val ec = system.executionContext

    val result: Future[PingBot.Reply] = system.ask(ref => PingBot.Ping("hello", ref))
    result.waitResult() match {
      case PingBot.Pong(msg) => println(s"received: $msg")
      case _ => println(s"failed to receive")
    }
    println("continue")
    system.terminate()
  }

}




