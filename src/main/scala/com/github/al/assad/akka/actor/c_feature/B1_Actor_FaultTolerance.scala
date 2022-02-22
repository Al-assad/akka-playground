package com.github.al.assad.akka.actor.c_feature

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed._
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration.DurationInt

/**
 * Actor fault tolerance sample.
 *
 * https://doc.akka.io/docs/akka/current/typed/fault-tolerance.html
 */
object ActorFaultTolerance {

  trait BasePingBot {
    sealed trait Command
    final case class Ping(errType: Int, replyTo: ActorRef[String]) extends Command

    def receiveBehavior(): Behavior[Command] = Behaviors.receiveMessage[Command] {
      case Ping(errType, replyTo) =>
        println(s"PingBot received Ping($errType)")
        errType match {
          case 0 => replyTo ! "pong"; Behaviors.same
          case 1 => throw new IllegalStateException("err:1 => boom!")
          case 2 => throw new IllegalAccessException("err:2 => boom!")
        }
    }.receiveSignal {
      // catch PreRestart and PostStop signal
      case (_, PreRestart) => println("PingBot restarted"); Behaviors.same
      case (_, PostStop) => println("PingBot stopped"); Behaviors.same
    }
  }

}

//noinspection DuplicatedCode
class ActorFaultToleranceSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  import ActorFaultTolerance._

  // Testing Supervision strategy
  "Supervision Strategy" should {
    "restart when fail caused by all type" in {
      // supervise all type of exception
      object PingBot extends BasePingBot {
        def apply(): Behavior[Command] =
          Behaviors.supervise {
            receiveBehavior()
          }.onFailure(SupervisorStrategy.restart)
      }

      val bot = spawn(PingBot())
      val probe = createTestProbe[String]()
      bot ! PingBot.Ping(1, probe.ref)
      bot ! PingBot.Ping(2, probe.ref)
      bot ! PingBot.Ping(0, probe.ref)
      probe.expectMessage("pong")
    }

    "restart when fail caused by specific type" in {
      // just supervise specific type of exception: IllegalStateException
      object PingBot extends BasePingBot {
        def apply(): Behavior[Command] =
          Behaviors.supervise {
            receiveBehavior()
          }.onFailure[IllegalStateException](SupervisorStrategy.restart)
      }

      val bot = spawn(PingBot())
      val probe = createTestProbe[String]()

      bot ! PingBot.Ping(1, probe.ref)
      probe.expectNoMessage()
      bot ! PingBot.Ping(0, probe.ref)
      probe.expectMessage("pong")

      bot ! PingBot.Ping(2, probe.ref)
      probe.expectTerminated(bot)
    }

    "restart with rate limit" in {
      //  restart no more than 10 times in a 10 second period
      object PingBot extends BasePingBot {
        def apply(): Behavior[Command] = Behaviors.supervise(receiveBehavior())
          .onFailure(SupervisorStrategy.restart.withLimit(10, 10.seconds))
      }

      // fail less than 10 times in a 10 second period
      val bot1 = spawn(PingBot())
      val probe1 = createTestProbe[String]()
      (1 to 5).foreach(_ => bot1 ! PingBot.Ping(1, probe1.ref))
      bot1 ! PingBot.Ping(0, probe1.ref)
      probe1.expectMessage("pong")

      // fail more than 10 times in a 10 second period
      val bot2 = spawn(PingBot())
      val probe2 = createTestProbe[String]()
      (1 to 20).foreach(_ => bot2 ! PingBot.Ping(1, probe2.ref))
      probe2.expectTerminated(bot2)
    }

    "stop when fail" in {
      object PingBot extends BasePingBot {
        def apply(): Behavior[Command] = Behaviors.supervise(receiveBehavior()).onFailure(SupervisorStrategy.stop)
      }
      val bot = spawn(PingBot())
      val probe = createTestProbe[String]()
      bot ! PingBot.Ping(1, probe.ref)
      probe.expectTerminated(bot)
    }

    "resume when fail" in {
      // resume means ignoring the failure and process the next message
      object PingBot extends BasePingBot {
        def apply(): Behavior[Command] = Behaviors.supervise(receiveBehavior()).onFailure(SupervisorStrategy.resume)
      }
      val bot = spawn(PingBot())
      val probe = createTestProbe[String]()
      bot ! PingBot.Ping(1, probe.ref)
      bot ! PingBot.Ping(2, probe.ref)
      bot ! PingBot.Ping(0, probe.ref)
      probe.expectMessage("pong")
    }
  }








}
