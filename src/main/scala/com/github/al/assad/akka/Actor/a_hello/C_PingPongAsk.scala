package com.github.al.assad.akka.Actor.a_hello

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, Terminated}
import akka.util.Timeout

import java.util.concurrent.CountDownLatch
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

/**
 * a simple ping-pong asking message example
 *
 * description: https://www.yangbajing.me/akka-cookbook/actor/pattern/actor-inside-ask.html
 * analysis: https://drive.google.com/file/d/1RVEJRJwEqSBT94Efp3VuVtvgzmmJuxHD/view?usp=sharing
 */


// Ping Actor
object Ping {
  sealed trait Request
  private final case class WrappedResponse(response: Pong.Response) extends Request

  def apply(latch: CountDownLatch): Behavior[Request] = Behaviors.setup { context: ActorContext[Request] =>
    implicit val timeout: Timeout = 2.seconds

    // spawn Pong actor and watch it.
    val pong = context.spawn(Pong(), "pong")
    context.watch(pong)
    // create the first ask
    context.ask(pong, (replyTo: ActorRef[Pong.Response]) => Pong.Message("hello scala!", 1, replyTo)) {
      case Success(value) => WrappedResponse(value)
      case Failure(exception) => throw exception
    }

    Behaviors
      .receiveMessage[Request] {
        case WrappedResponse(Pong.Result(message, count)) =>
          context.log.info(s"received message: $message, ${count}th.")
          // The response mapping function of context.ask runs in the receiving actor
          // and provides safe access to the internal state of the actor,
          // but the actor will be stopped when an exception is thrown.
          context.ask[Pong.Request, Pong.Response](pong, Pong.Message(message, count + 1, _)) {
            case Success(value) => WrappedResponse(value)
            case Failure(exception) => throw exception
          }
          Behaviors.same
      }
      // watch signal of Pong Actor
      .receiveSignal {
        case (context, Terminated(`pong`)) =>
          context.log.info(s"Actor $pong be terminated.")
          latch.countDown()
          Behaviors.stopped
      }
  }

}

// Pong Actor
object Pong {
  sealed trait Request
  sealed trait Response
  final case class Message(message: String, count: Int, replyTo: ActorRef[Response]) extends Request
  final case class Result(message: String, count: Int) extends Response

  def apply(): Behavior[Request] = Behaviors.receive {
    case (context, Message(message, 100, _)) =>
      context.log.info(s"received 100th Ping message: $message, it will stop.")
      Behaviors.stopped
    case (_, Message(message, count, replyTo)) =>
      replyTo ! Result(message, count)
      Behaviors.same
  }
}

// Actor System
object PingPongMain {
  def main(args: Array[String]): Unit = {
    val latch = new CountDownLatch(1)
    val system = ActorSystem(Ping(latch), "ping-pong")
    latch.await()
    system.terminate()
  }
}


