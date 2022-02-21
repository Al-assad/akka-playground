package com.github.al.assad.akka.actor.b_interaction

import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import akka.actor.typed.scaladsl.AskPattern.{Askable, schedulerFromActorSystem}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.util.Timeout
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

/**
 * Actor tell and ask sample
 * - tell use case.
 * - ask use case.
 * - ask between actors.
 * - ask outside actor system.
 */
object TellAndAskActor {

  object PingBot {

    sealed trait Command
    final case object Touch extends Command
    final case class Ping(replyTo: ActorRef[String]) extends Command

    def apply(): Behavior[Command] = Behaviors.receiveMessage {
      case Touch =>
        println("PingBot receive Touch")
        Behaviors.same
      case Ping(replyTo) =>
        println("PingBot receive Ping")
        replyTo ! "pong"
        Behaviors.same
    }
  }

  object BotManager {

    sealed trait Command
    final case class Touch(botId: Int) extends Command
    final case class Ping(botId: Int, replyTo: ActorRef[String]) extends Command

    // result wrapper
    sealed trait Internal extends Command
    private final case class PingBotResponseAdapter(msg: String) extends Internal
    private final case class PingBotReplyResponseAdapter(msg: String, replyTo: ActorRef[String]) extends Internal
    implicit val timeout: Timeout = 3.seconds

    def apply(): Behavior[Command] = Behaviors.setup { ctx =>
      val bots = (0 to 5).map(i => ctx.spawn(PingBot(), s"PingBot-$i"))

      Behaviors.receiveMessage {

        case Touch(botId) =>
          println(s"BotManager receive Touch $botId")
          // ask between actors
          ctx.ask[PingBot.Ping, String](bots(botId), ref => PingBot.Ping(ref)) {
            case Success(msg) => PingBotResponseAdapter(msg) // covert to BotManager.Command
            case Failure(ex) => PingBotResponseAdapter(ex.getMessage)
          }
          Behaviors.same

        case Ping(botId, replyTo) =>
          println(s"BotManager receive Ping $botId")
          // ask between actors and reply to outside actor
          ctx.ask(bots(botId), PingBot.Ping) {
            case Success(msg) => PingBotReplyResponseAdapter(msg, replyTo)
            case Failure(ex) => PingBotReplyResponseAdapter(ex.getMessage, replyTo)
          }
          Behaviors.same

        case PingBotResponseAdapter(msg) =>
          println(s"BotManager receive PingBotResponseAdapter $msg")
          Behaviors.same

        case PingBotReplyResponseAdapter(msg, replyTo) =>
          replyTo ! msg
          Behaviors.same
      }
    }
  }
}

class TellAndAskActorSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {
  import TellAndAskActor._

  "PingBot" should {
    "tell message" in {
      val bot = spawn(PingBot())
      bot ! PingBot.Touch
    }
    "ask message outside actor system via Future" in {
      val bot = spawn(PingBot())
      // or bot ? (PingBot.Ping(_))
      bot ? (ref => PingBot.Ping(ref)) onComplete {
        case Success(msg) =>
          println(s"Ask message outside actor system: $msg")
        case Failure(ex) => println(ex)
      }
    }
    "ask message outside actor system via TestProbe" in {
      val bot = spawn(PingBot())
      val probe = TestProbe[String]()
      bot ! PingBot.Ping(probe.ref)
      println("Ask message outside actor system:" + probe.receiveMessage())
    }
  }

  "BotManager" should {
    "touch bot" in {
      val botManager = spawn(BotManager())
      botManager ! BotManager.Touch(0)
      botManager ! BotManager.Touch(1)
    }
    "ping bot" in {
      val botManager = spawn(BotManager())
      val probe = TestProbe[String]()
      botManager ! BotManager.Ping(0, probe.ref)
      probe.expectMessage("pong")
    }
  }

}
