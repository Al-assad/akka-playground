package com.github.al.assad.akka.Actor.b_interaction

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.github.al.assad.akka.Actor.b_interaction.ActorSchedulingMessageToSelf2.Buffer
import org.scalatest.wordspec.AnyWordSpecLike

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.language.postfixOps

/**
 * Use timers to schedule messages to an actor, This is another implementation of [[ ActorSchedulingMessageToSelf.MsgBuffer ]].
 *
 * https://doc.akka.io/docs/akka/current/typed/interaction-patterns.html#scheduling-messages-to-self
 */
object ActorSchedulingMessageToSelf2 {

  object Buffer {
    sealed trait Command
    final case class Send(msg: String) extends Command
    final case class BatchValues(message: Seq[String]) extends Command

    sealed trait Internal extends Command
    private case object Timeout extends Internal
    private case object ResetTimer extends Internal

    private case object BufferTimeKey

    def apply(target: ActorRef[BatchValues], timeLimit: FiniteDuration, sizeLimit: Int): Behavior[Command] =
      Behaviors.withTimers { timers =>
        val buffer = ArrayBuffer.empty[String]
        resetTimer()

        def resetTimer() = {
          timers.cancel(BufferTimeKey)
          timers.startSingleTimer(BufferTimeKey, Timeout, timeLimit)
        }

        Behaviors.receiveMessage[Command] {
          case Timeout =>
            target ! BatchValues(buffer.toSeq)
            buffer.clear()
            resetTimer()
            Behaviors.same

          case Send(msg) =>
            if (buffer.size == sizeLimit) {
              target ! BatchValues(buffer.toSeq)
              buffer.clear()
              resetTimer()
            }
            buffer += msg
            Behaviors.same
        }
      }
  }

}


class ActorSchedulingMessageToSelf2Spec extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  import ActorSchedulingMessageToSelf2.Buffer._

  "Buffer" should {
    "limited by time" in {
      val probe = createTestProbe[BatchValues]()
      val buffer = spawn(Buffer(probe.ref, 1.second, 10))

      buffer ! Send("1")
      buffer ! Send("2")
      probe.expectNoMessage()
      probe.receiveMessage(2.seconds).message.size shouldBe 2

      buffer ! Send("3")
      probe.expectNoMessage()
      probe.receiveMessage(2.seconds).message.size shouldBe 1

    }
    "limited by size" in {
      val probe = createTestProbe[BatchValues]()
      val buffer = spawn(Buffer(probe.ref, 4.second, 3))

      (1 to 4).foreach(i => buffer ! Send(i.toString))
      probe.receiveMessage().message shouldBe Seq("1", "2", "3")

      probe.receiveMessage(10.seconds).message shouldBe Seq("4")

      (5 to 9).foreach(i => buffer ! Send(i.toString))
      probe.receiveMessage().message shouldBe Seq("5", "6", "7")
    }
  }

}

