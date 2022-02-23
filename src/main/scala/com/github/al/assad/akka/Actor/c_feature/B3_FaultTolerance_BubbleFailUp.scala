package com.github.al.assad.akka.Actor.c_feature

import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import akka.actor.typed._
import akka.actor.typed.scaladsl.Behaviors
import com.github.al.assad.akka.Actor.c_feature.ActorFaultToleranceBubbleFailUp.Protocol.{Fail, Hello}
import org.scalatest.wordspec.AnyWordSpecLike

/**
 * Actor fault tolerance between parent and child sample.
 *
 * There might be cases when you want the original exception to bubble up the hierarchy,
 * this can be done by handling the Terminated signal, and rethrowing the exception in each actor.
 * If the parent in turn does not handle the Terminated message it will itself fail with an DeathPactException.
 *
 * https://doc.akka.io/docs/akka/current/typed/fault-tolerance.html
 */
object ActorFaultToleranceBubbleFailUp {

  object Protocol {
    sealed trait Command
    case class Fail(text: String) extends Command
    case class Hello(text: String, replyTo: ActorRef[String]) extends Command
  }

  import Protocol._

  object Worker {
    def apply(): Behavior[Command] = Behaviors.receiveMessage {
        case Fail(text) =>
          throw new RuntimeException(text)
        case Hello(text, replyTo) =>
          replyTo ! text
          Behaviors.same
      }
  }

  object MiddleManager {
    def apply(): Behavior[Command] =
      Behaviors.setup[Command] { context =>
        context.log.info("Middle management starting up")
        // default supervision of child, meaning that it will stop on failure
        val child = context.spawn(Worker(), "child")
        // we want to know when the child terminates, but since we do not handle
        // the Terminated signal, we will in turn fail on child termination
        context.watch(child)

        // here we don't handle Terminated at all which means that
        // when the child fails or stops gracefully this actor will
        // fail with a DeathPactException
        Behaviors.receiveMessage { message =>
          child ! message
          Behaviors.same
        }
      }
  }

  object Boss {
    def apply(): Behavior[Command] =
      Behaviors
        .supervise(Behaviors.setup[Command] { context =>
          context.log.info("Boss starting up")
          // default supervision of child, meaning that it will stop on failure
          val middleManagement = context.spawn(MiddleManager(), "middle-management")
          context.watch(middleManagement)

          // here we don't handle Terminated at all which means that
          // when middle management fails with a DeathPactException
          // this actor will also fail
          Behaviors.receiveMessage[Command] { message =>
            middleManagement ! message
            Behaviors.same
          }
        })
        .onFailure[DeathPactException](SupervisorStrategy.restart)
  }


}

//noinspection DuplicatedCode
class ActorFaultToleranceBubbleFailUpSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  import ActorFaultToleranceBubbleFailUp._

  "Bubble failures up through the hierarchy" should {
    "test1" in {
      val boss = spawn(Boss(), "boss")
      boss ! Fail("fail")
      val probe = TestProbe[String]()
      boss ! Hello("hello", probe.ref)
      probe.expectMessage("hello")
    }
  }

}
