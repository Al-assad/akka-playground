package com.github.al.assad.akka.Actor.d_other

import akka.actor.typed.scaladsl.AskPattern.{Askable, schedulerFromActorSystem}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, PostStop, PreRestart, SupervisorStrategy, Terminated}
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout
import com.github.al.assad.akka.Actor.d_other.TestingActorRefWhenRestart.MyGuardian.GetActor
import com.github.al.assad.akka.TestImplicit.WrappedFuture
import com.github.al.assad.akka.sleep

import scala.concurrent.duration.DurationInt

/**
 * Testing whether the consistency of ActorRef after restarted.
 */
object TestingActorRefWhenRestart {

  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem(MyGuardian(), "MyGuardian")
    implicit val timeout: Timeout = 5.seconds
    val actor1 = (system ? GetActor).receive()

    actor1 ! MyActor.Ping // pong
    // restart actor1
    actor1 ! MyActor.ThrowError
    actor1 ! MyActor.Ping // pong

    sleep(5.seconds)

    val actor2 = (system ? GetActor).receive()
    actor2 ! MyActor.Ping // pong

    println("actor1 == actor2", actor1 == actor2) // true
  }


  object MyGuardian {
    sealed trait Command
    case class GetActor(replyTo: ActorRef[ActorRef[MyActor.Command]]) extends Command

    def apply(): Behavior[Command] = Behaviors.setup { context =>
      context.log.info("MyGuardian is alive")
      val actor = context.spawn(MyActor(), "MyActor")
      context.watch(actor)

      Behaviors.receiveMessage[Command] {
        case GetActor(replyTo) =>
          replyTo ! actor
          Behaviors.same
      }.receiveSignal {
        case (_, Terminated(`actor`)) =>
          context.log.info("MyActor is terminated")
          Behaviors.same
      }
    }
  }

  object MyActor {
    sealed trait Command
    case object ThrowError extends Command
    case object Ping extends Command

    def apply(): Behavior[Command] = Behaviors.supervise[Command] {
      Behaviors.setup[Command] { ctx =>
        Behaviors.receiveMessage[Command] {
          case Ping =>
            ctx.log.info("pong")
            Behaviors.same
          case ThrowError =>
            ctx.log.error("MyActor shutdown")
            throw new RuntimeException("MyActor shutdown")
        }.receiveSignal {
          case (context, PreRestart) => context.log.info("MyActor restart"); Behaviors.same
          case (context, PostStop) => context.log.info("MyActor stop"); Behaviors.same
        }
      }
    }.onFailure(SupervisorStrategy.restart)

  }


}
