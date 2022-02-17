package com.github.al.assad.akka.actor.b_base

import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import akka.actor.typed.scaladsl.AskPattern.{Askable, schedulerFromActorSystem}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.github.al.assad.akka.actor.b_base.MultipleServiceSample.{Guardian, ServiceA, ServiceB}
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

object MultipleServiceSample {

  object ServiceA {
    sealed trait Command extends Guardian.Command

    final case object Reach extends Command
    final case class Ping(msg: String, replyTo: ActorRef[Pong]) extends Command
    sealed trait Reply extends Command
    final case class Pong(msg: String) extends Reply

    def apply(): Behavior[Command] = Behaviors.receiveMessage {
      case Reach =>
        println("ServiceA receive: Reach")
        Behaviors.same
      case Ping(msg, replyTo) =>
        println(s"ServiceA receive: ${msg}")
        replyTo ! Pong(s"pong ${msg}")
        Behaviors.same
    }
  }

  object ServiceB {
    sealed trait Command extends Guardian.Command

    final case object Ping extends Command
    def apply(): Behavior[Command] = Behaviors.receiveMessage {
      case Ping =>
        println("ServiceB receive: Ping")
        Behaviors.same
    }
  }

  object Guardian {
    sealed trait Command

    def apply(): Behavior[Command] = Behaviors.setup { ctx =>
      val serviceA = ctx.spawn(ServiceA(), "service-A")
      val serviceB = ctx.spawn(ServiceB(), "service-B")
      Behaviors.receiveMessage {
        case cmd: ServiceA.Command => serviceA ! cmd; Behaviors.same
        case cmd: ServiceB.Command => serviceB ! cmd; Behaviors.same
      }
    }
  }

}

class MultipleServiceSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {
  "MultipleService" should {

    "test1" in {
      val guardian = spawn(Guardian(), "guardian")
      guardian ! ServiceA.Reach
      guardian ! ServiceB.Ping

      // ask and receive result to TestProbe
      val probe = TestProbe[ServiceA.Pong]
      guardian ! ServiceA.Ping("ping", probe.ref)
      println(probe.receiveMessage())

      // ask and receive result to Future
      val f = guardian ? (ref => ServiceA.Ping("ping", ref))
      println(Await.result(f, 3.seconds).msg)
    }

  }
}
