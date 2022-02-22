package com.github.al.assad.akka.actor.c_feature

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed._
import akka.actor.typed.scaladsl.Behaviors
import com.github.al.assad.akka.actor.c_feature.ActorFaultTolerance.BasePingBot
import org.scalatest.wordspec.AnyWordSpecLike

/**
 * Actor fault tolerance between parent and child sample.
 *
 * https://doc.akka.io/docs/akka/current/typed/fault-tolerance.html
 */
object ActorFaultToleranceBetweenParentAndChild {

  object AutoRestartPingBot extends BasePingBot {
    def apply(): Behavior[Command] = Behaviors.supervise(receiveBehavior()).onFailure(SupervisorStrategy.restart)
  }

  trait BasePingBotManager {
    sealed trait Command
    final case object LetUsCrash extends Command
    final case class OfferPingBot(replyTo: ActorRef[ActorRef[AutoRestartPingBot.Command]]) extends Command

    def receiveBehavior(bot: ActorRef[AutoRestartPingBot.Command]): Behavior[Command] = Behaviors.receiveMessage {
      case LetUsCrash => throw new IllegalStateException("PingBotManager crash!")
      case OfferPingBot(replyTo) =>
        replyTo ! bot
        Behaviors.same
    }
  }

}

//noinspection DuplicatedCode
class ActorFaultToleranceBetweenParentAndChildSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  import ActorFaultToleranceBetweenParentAndChild._

  // Testing fault tolerance between parent and child actors
  "Supervision between parent and child" should {

    "child actors are stopped when parent is restarting" in {

      object PingBotManager extends BasePingBotManager{
        def apply(): Behavior[Command] = Behaviors.setup { ctx =>
          val bot = ctx.spawn(AutoRestartPingBot(), "bot")
          Behaviors.supervise[Command] {
            receiveBehavior(bot)
          }.onFailure(SupervisorStrategy.restart)
        }
      }

      val manager = spawn(PingBotManager())
      val probe = createTestProbe[ActorRef[AutoRestartPingBot.Command]]()

      val botBefore = {
        manager ! PingBotManager.OfferPingBot(probe.ref)
        probe.receiveMessage()
      }

      manager ! PingBotManager.LetUsCrash
      // bot is stopped
      probe.expectTerminated(botBefore)

      val botAfter = {
        manager ! PingBotManager.OfferPingBot(probe.ref)
        probe.receiveMessage()
      }
      // bot is stopped
      probe.expectTerminated(botAfter)
    }


    "child actors are kept alive when parent is restarting" in {

      object PingBotManager extends BasePingBotManager{
        def apply(): Behavior[Command] = Behaviors.setup { ctx =>
          val bot = ctx.spawn(AutoRestartPingBot(), "bot")
          Behaviors.supervise[Command] {
            receiveBehavior(bot)
          }.onFailure(SupervisorStrategy.restart.withStopChildren(false))
        }
      }

      val manager = spawn(PingBotManager())
      val probe = createTestProbe[ActorRef[AutoRestartPingBot.Command]]()

      manager ! PingBotManager.LetUsCrash
      val botBefore = {
        manager ! PingBotManager.OfferPingBot(probe.ref)
        probe.receiveMessage()
      }
      // bot is kept alive
      assertThrows[AssertionError](probe.expectTerminated(botBefore))
      val botAfter = {
        manager ! PingBotManager.OfferPingBot(probe.ref)
        probe.receiveMessage()
      }
      // bot is kept alive
      assertThrows[AssertionError](probe.expectTerminated(botAfter))
    }

  }


}
