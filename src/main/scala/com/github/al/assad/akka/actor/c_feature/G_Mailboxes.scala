package com.github.al.assad.akka.actor.c_feature

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, MailboxSelector}
import com.github.al.assad.akka.sleep
import com.typesafe.config.ConfigFactory
import org.scalatest.wordspec.AnyWordSpecLike

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

/**
 * Actor mailboxes sample.
 *
 * https://doc.akka.io/docs/akka/current/typed/mailboxes.html
 *
 * By default an unbounded mailbox is used, this means any number of messages can be enqueued
 * into the mailbox, this can lead to the application running out of memory.
 *
 * For this reason a bounded mailbox can be specified, the bounded mailbox will pass new messages
 * to deadletters when the mailbox is full.
 */
object ActorMailboxes {

  object PrintActor {
    sealed trait Command
    final case class Print(msg: String, priority: Int = 1) extends Command
    final case class GetStatResult(replyTo: ActorRef[Map[Int, Int]]) extends Command
    private case class Stat(priority: Int) extends Command

    def apply(): Behavior[Command] = Behaviors.setup { ctx =>
      val stat = mutable.Map.empty[Int, Int]

      Behaviors.receiveMessage {
        case Print(msg, priority) =>
          ctx.pipeToSelf(Future {
            sleep(1.seconds)
            println(s"PrintActor[$priority]: $msg")
          }) {
            _ => Stat(priority)
          }
          Behaviors.same
        case Stat(priority) =>
          stat(priority) = stat.getOrElse(priority, 0) + 1
          Behaviors.same
        case GetStatResult(replyTo) =>
          replyTo ! stat.toMap
          Behaviors.same
      }
    }
  }

  trait BasePrintManager {
    sealed trait Command
    final case class Print(msg: String, priority: Int = 1) extends Command
    final case class GetStatResult(replyTo: ActorRef[Map[Int, Int]]) extends Command

    def active(printActor: ActorRef[PrintActor.Command]): Behavior[Command] = Behaviors.receiveMessage {
      case Print(msg, priority) =>
        printActor ! PrintActor.Print(msg, priority)
        Behaviors.same
      case GetStatResult(replyTo) =>
        printActor ! PrintActor.GetStatResult(replyTo)
        Behaviors.same
    }
  }


  // Testing unbounded mailbox
  object UnboundedMailboxPrintManager extends BasePrintManager {
    def apply(): Behavior[Command] = Behaviors.setup { context =>
      // limit the unbounded mailbox capacity to 10,
      // When the message limit is reached, subsequent messages will be discarded.
      val printActor = context.spawn(PrintActor(), "unbounded-mailbox-print-actor", MailboxSelector.bounded(10))
      active(printActor)
    }
  }

  // Testing unbounded mailbox via config
  object UnboundedMailboxViaConfigPrintManager extends BasePrintManager {
    def apply(): Behavior[Command] = Behaviors.setup { context =>
      val probs = MailboxSelector.fromConfig("my-app.my-special-mailbox")
      val printActor = context.spawn(PrintActor(), "unbounded-mailbox-print-actor", probs)
      active(printActor)
    }
  }

}

//noinspection DuplicatedCode
class ActorMailboxesSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  import ActorMailboxes._

  "Actor Mailboxes" should {

    "UnboundedMailboxPrintManager" in {
      import UnboundedMailboxPrintManager._
      val manager = spawn(UnboundedMailboxPrintManager())

      (1 to 9).foreach(i => manager ! Print(s"msg-$i", 1))
      (10 to 19).foreach(i => manager ! Print(s"msg-$i", 2))
      (20 to 29).foreach(i => manager ! Print(s"msg-$i", 3))

      sleep(10.seconds)
      val probe = createTestProbe[Map[Int, Int]]()
      manager ! GetStatResult(probe.ref)
      println(probe.receiveMessage())
      // maybe: Map(1 -> 9, 2 -> 2)
    }

    "UnboundedMailboxViaConfigPrintManager" in {
      val config = ConfigFactory.parseString(
        """
          |my-app {
          |  my-special-mailbox {
          |    mailbox-type = "akka.dispatch.UnboundedMailbox"
          |    mailbox-capacity = 10
          |  }
          |}
        """.stripMargin)
      val system2 = ActorSystem(UnboundedMailboxViaConfigPrintManager(), "mailboxes-test", config)

      import UnboundedMailboxViaConfigPrintManager._
      (1 to 100).foreach(i => system2 ! Print(s"msg-$i"))
      sleep(20.seconds)
    }

  }
}
