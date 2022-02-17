package com.github.al.assad.akka.actor.b_base

import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.AskPattern.{Askable, schedulerFromActorSystem}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.util.Timeout
import com.github.al.assad.akka.actor.b_base.E_ReceptionistServiceKey.PingBot.PingBotServiceKey
import com.github.al.assad.akka.actor.b_base.E_ReceptionistServiceKey.{PingBot, PingBotManager}
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}

/**
 * Access to other actor via ServiceKey
 *
 * https://doc.akka.io/docs/akka/current/typed/actor-discovery.html
 * http://www.yangbajing.me/scala-web-development/actor/actor.html
 *
 */
object E_ReceptionistServiceKey {

  object PingBot {
    val PingBotServiceKey = ServiceKey[Command]("pingBot")

    sealed trait Command
    final case class Ping(replyTo: ActorRef[String]) extends Command
    private final case class ServiceKeyRegistered(registered: Receptionist.Registered) extends Command

    def apply(): Behavior[Command] = Behaviors.setup { ctx =>
      // register to the receptionist
      val registerAdapter = ctx.messageAdapter[Receptionist.Registered](value => ServiceKeyRegistered(value))
      ctx.system.receptionist ! Receptionist.Register(PingBotServiceKey, ctx.self, registerAdapter)
//      ctx.system.receptionist ! Receptionist.Register(PingBotServiceKey, ctx.self)

      Behaviors.receiveMessage[Command] {
        case Ping(replyTo) =>
          replyTo ! "pong"
          println(s"PingBot: received ping")
          Behaviors.same
      }
    }
  }

  object PingBotManager {

    sealed trait Command
    final case object PingSilent extends Command
    final case class Ping(replyTo: ActorRef[String]) extends Command

    final case class WrappedPong(msg: String) extends Command

    implicit val timeout: Timeout = 2.seconds

    def apply(): Behavior[Command] = Behaviors.setup { ctx =>
      val bot = ctx.spawn(PingBot(), "ping-bot")
      ctx.watch(bot)

      Behaviors.receiveMessage[Command] {
        case PingSilent =>
          ctx.ask(bot, replyTo => PingBot.Ping(replyTo)) {
            case Success(msg) => WrappedPong(msg)
            case Failure(ex) => throw ex
          }
          Behaviors.same
        case Ping(reply) =>
          ctx.ask(bot, replyTo => PingBot.Ping(replyTo)) {
            case Success(replyMsg) => reply ! replyMsg; WrappedPong(replyMsg)
            case Failure(ex) => throw ex
          }
          Behaviors.same
        case WrappedPong(msg) =>
          println(s"Got pong: $msg")
          Behaviors.same
      }
    }
  }

}

class ReceptionServiceKeySpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  "spec" should {

    "Directly ask PingBot" in {
      val bot = spawn(PingBot())
      val probe = TestProbe[String]
      bot ! PingBot.Ping(probe.ref)
      println(probe.receiveMessage())
    }

    "Directly ask PingBotManager" in {
      val manager = spawn(PingBotManager())
      val probe = TestProbe[String]
      manager ! PingBotManager.Ping(probe.ref)
      println(probe.receiveMessage())
    }

    "ask PingBot via ServiceKey" in {
//      implicit val system = ActorSystem(PingBotManager(), "ping-bot-manager")
      val a = spawn(PingBotManager())

      val pingBotActorFuture: Future[ActorRef[PingBot.Command]] = system.receptionist
          .ask[Receptionist.Listing](Receptionist.Find(PingBotServiceKey))
          // get the first registered PingBotServiceKey actor
          .map { listing =>
            if (listing.isForKey(PingBotServiceKey)) listing.serviceInstances(PingBotServiceKey).head
            else throw new RuntimeException("PingBotServiceKey not found")
          }

      val pingBotActorRef = Await.result(pingBotActorFuture, 3.seconds)
      val probe = TestProbe[String]
      pingBotActorRef ! PingBot.Ping(probe.ref)
      println(probe.receiveMessage())
    }


  }
}

