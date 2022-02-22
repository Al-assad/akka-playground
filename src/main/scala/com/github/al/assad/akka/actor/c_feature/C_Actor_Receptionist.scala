package com.github.al.assad.akka.actor.c_feature

import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.AskPattern.{Askable, schedulerFromActorSystem}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.github.al.assad.akka.TestImplicit.TestProbeWrapper
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.postfixOps
import scala.util.Success

/**
 * https://doc.akka.io/docs/akka/current/typed/actor-discovery.html
 *
 * There are two general ways to obtain Actor references:
 * - by creating actors;
 * - by discovery using the Receptionist;
 *
 * When an actor needs to be discovered by another actor but you are unable
 * to put a reference to it in an incoming message, you can use the Receptionist.
 *
 */
object ActorReceptionist {

  object PingBot {
    // define ServiceKey
    val PingBotKey = ServiceKey[Ping]("PingService")

    sealed trait Command
    final case class Ping(replyTo: ActorRef[String]) extends Command

    def apply(): Behavior[Command] = Behaviors.setup { ctx =>
      // register ServiceKey with PingBot to context
      ctx.system.receptionist ! Receptionist.Register(PingBot.PingBotKey, ctx.self)

      Behaviors.receiveMessage {
        case Ping(replyTo) =>
          println("PingBot receive Ping")
          replyTo ! "pong"
          Behaviors.same
      }
    }
  }

  // PingBotGuardian: Spawn PingBot
  object PingBotGuardian {
    sealed trait Command
    final case class Touch(replyTo: ActorRef[String]) extends Command

    def apply(): Behavior[Command] = Behaviors.setup { ctx =>
      val pingBot = ctx.spawn(PingBot(), "PingBot")

      Behaviors.receiveMessage {
        case Touch(replyTo) =>
          pingBot ! PingBot.Ping(replyTo)
          Behaviors.same
      }
    }
  }

  // ThirdPinger: Interact with PingBot via ServiceKey
  object ThirdPinger {
    sealed trait Command
    final case object Touch extends Command
    final case class Ping(replyTo: ActorRef[String]) extends Command

    private case class InternalListingRes(listing: Receptionist.Listing) extends Command
    private case class InternalPingListingRes(listing: Receptionist.Listing, replyTo: ActorRef[String]) extends Command

    def apply(): Behavior[Command] = Behaviors.setup { ctx =>
      Behaviors.receiveMessage {
        case Touch =>
          // find PingBotKey from receptionist
          ctx.system.receptionist ! Receptionist.Find(
            PingBot.PingBotKey,
            ctx.messageAdapter[Receptionist.Listing](InternalListingRes.apply)
          )
          Behaviors.same

        case Ping(replyTo) =>
          // find PingBotKey from receptionist
          ctx.system.receptionist ! Receptionist.Find(
            PingBot.PingBotKey,
            ctx.messageAdapter[Receptionist.Listing](ref => InternalPingListingRes(ref, replyTo))
          )
          Behaviors.same

        case InternalListingRes(listing) =>
          listing.serviceInstances(PingBot.PingBotKey).head ! PingBot.Ping(ctx.system.ignoreRef)
          Behaviors.same

        case InternalPingListingRes(listing, replyTo) =>
          listing.serviceInstances(PingBot.PingBotKey).head ! PingBot.Ping(replyTo)
          Behaviors.same
      }
    }

  }

}

class ActorReceptionistSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  import ActorReceptionist._

  "Actor Actor Receptionist" should {
    "call PingBotManager normally" in {
      val manager = spawn(PingBotGuardian())
      manager ! PingBotGuardian.Touch(system.ignoreRef)
    }

    "use service key to access actor" in {
      spawn(PingBotGuardian())
      val pinger = spawn(ThirdPinger())
      pinger ! ThirdPinger.Touch

      createTestProbe[String] to { probe =>
        pinger ! ThirdPinger.Ping(probe.ref)
        probe.expectMessage("pong")
      }
    }

    "use service key to access actor outside akka system via Future" in {
      spawn(PingBotGuardian())
      system.receptionist ? Receptionist.Find(PingBot.PingBotKey) map { listing =>
        val pingBot = listing.serviceInstances(PingBot.PingBotKey).head
        pingBot ? (ref => PingBot.Ping(ref)) onComplete {
          case Success(pong) => pong shouldBe "pong"
          case _ => fail
        }
      }
    }

    "use service key to access actor outside akka system via TestProbe" in {
      spawn(PingBotGuardian())

      val listingProbe = TestProbe[Receptionist.Listing]()
      system.receptionist ! Receptionist.Find(PingBot.PingBotKey, listingProbe.ref)
      val pingBot = listingProbe.receiveMessage().serviceInstances(PingBot.PingBotKey).head

      val pingProbe = TestProbe[String]()
      pingBot ! PingBot.Ping(pingProbe.ref)
      pingProbe.expectMessage("pong")
    }

  }

}
