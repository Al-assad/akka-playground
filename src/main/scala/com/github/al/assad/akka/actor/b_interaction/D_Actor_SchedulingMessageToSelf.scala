package com.github.al.assad.akka.actor.b_interaction

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{Behaviors, TimerScheduler}
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration.{DurationInt, FiniteDuration}

/**
 * Use timers to schedule messages to an actor.
 *
 * https://doc.akka.io/docs/akka/current/typed/interaction-patterns.html#scheduling-messages-to-self
 */
object ActorSchedulingMessageToSelf {

  object MsgBuffer {
    sealed trait Command
    final case class Send(msg: String) extends Command
    final case class Batch(message: Seq[Command]) extends Command

    sealed trait Internal extends Command
    private final case object Timeout extends Internal
    private case object BufferTimeKey

    def apply(target: ActorRef[Batch], bufferTimeLimit: FiniteDuration, bufferSizeLimit: Int): Behavior[Command] = {
      Behaviors.withTimers(timers => new MsgBuffer(timers, target, bufferTimeLimit, bufferSizeLimit).idle())
    }
  }

  class MsgBuffer(timers: TimerScheduler[MsgBuffer.Command],
                  flushTarget: ActorRef[MsgBuffer.Batch],
                  bufferTimeLimit: FiniteDuration,
                  bufferSizeLimit: Int) {
    import MsgBuffer._

    def idle(): Behavior[Command] = Behaviors.receiveMessage { message =>
      timers.startSingleTimer(BufferTimeKey, Timeout, bufferTimeLimit)
      active(Seq(message))
    }

    def active(buffer: Seq[Command]): Behavior[Command] = Behaviors.receiveMessage {
      case Timeout =>
        flushTarget ! Batch(buffer)
        idle()
      case msg =>
        val newBuffer = buffer :+ msg
        if (newBuffer.size == bufferSizeLimit) {
          timers.cancel(BufferTimeKey)
          flushTarget ! Batch(newBuffer)
          idle()
        } else {
          active(newBuffer)
        }
    }
  }
}


class ActorSchedulingMessageToSelfSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  import com.github.al.assad.akka.actor.b_interaction.ActorSchedulingMessageToSelf._

  "MsgBuffer" should {
    "limit by time" in {
      val probe =createTestProbe[MsgBuffer.Batch]()
      val buffer = spawn(MsgBuffer(probe.ref, 1.second, 10))
      buffer ! MsgBuffer.Send("1")
      buffer ! MsgBuffer.Send("2")
      probe.expectNoMessage()
      println(probe.receiveMessage(2.seconds))
    }
    "limit by size" in {
      val probe =createTestProbe[MsgBuffer.Batch]()
      val buffer = spawn(MsgBuffer(probe.ref, 5.second, 10))
      (1 to 11).foreach(i => buffer ! MsgBuffer.Send(i.toString))
      println(probe.receiveMessage())
    }
  }
}
