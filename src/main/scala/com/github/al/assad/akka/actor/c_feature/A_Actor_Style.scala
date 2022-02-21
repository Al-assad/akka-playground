package com.github.al.assad.akka.actor.c_feature

import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import com.github.al.assad.akka.TestImplicit.TestProbeWrapper
import com.github.al.assad.akka.actor.c_feature.ActorStyleFunctional.Counter
import org.scalatest.wordspec.AnyWordSpecLike

/**
 * https://doc.akka.io/docs/akka/current/typed/style-guide.html#functional-versus-object-oriented-style
 *
 * There are two main implementation styles for typed actor:
 * 1. functional style
 * 2. object-oriented style
 */

// functional style
object ActorStyleFunctional {

  object Counter {
    sealed trait Command
    case object Increment extends Command
    final case class GetValue(replyTo: ActorRef[Value]) extends Command
    final case class Value(n: Int)

    def apply(): Behavior[Command] = counter(0)

    private def counter(n: Int): Behavior[Command] =
      Behaviors.receive { (context, message) =>
        message match {
          case Increment =>
            val newValue = n + 1
            context.log.info(s"Incremented counter to $newValue")
            counter(newValue)
          case GetValue(replyTo) =>
            replyTo ! Value(n)
            Behaviors.same
        }
      }
  }
}

// object-oriented style
object ActorStyleObjectOriented {

  object Counter {
    sealed trait Command
    case object Increment extends Command
    final case class GetValue(replyTo: ActorRef[Value]) extends Command
    final case class Value(n: Int)

    def apply(): Behavior[Command] = {
//      Behaviors.setup(context => new Counter(context))
      Behaviors.setup(context => new Counter(context))
    }
  }

  class Counter(context: ActorContext[Counter.Command]) extends AbstractBehavior[Counter.Command](context) {
    import Counter._

    private var n = 0

    override def onMessage(msg: Command): Behavior[Counter.Command] = {
      msg match {
        case Increment =>
          n += 1
          context.log.debug("Incremented counter to [{}]", n)
          this
        case GetValue(replyTo) =>
          replyTo ! Value(n)
          this
      }
    }
  }
}


class ActorStyleSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {
  "Counter" should {

    "functional style" in {
      val counter = spawn(Counter())
      counter ! Counter.Increment

      TestProbe[Counter.Value]() to { probe =>
        counter ! Counter.GetValue(probe.ref)
        probe.expectMessage(Counter.Value(1))
      }
    }
  }
}


