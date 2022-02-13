package com.github.al.assad.akka.cluster.b_ddata

import akka.actor.testkit.typed.scaladsl.{LogCapturing, ScalaTestWithActorTestKit, TestProbe}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.ddata.typed.scaladsl.{DistributedData, Replicator}
import akka.cluster.ddata.{PNCounter, PNCounterKey}
import com.github.al.assad.akka.cluster.b_ddata.PNGCounterCache.{Decrement, GetValue, Increment}
import org.scalatest.wordspec.AnyWordSpecLike

/**
 * Akka Distributed Data PNCounter Demo.
 * PNCounter supports both increment and decrement operations.
 *
 * https://doc.akka.io/docs/akka/current/typed/distributed-data.html#counters
 */
object PNGCounterCache {

  sealed trait Command
  final case class Increment(value: Int) extends Command
  final case class Decrement(value: Int) extends Command
  final case class GetValue(replyTo: ActorRef[Int]) extends Command

  private sealed trait InternalCommand extends Command
  private final case class InternalUpdate(rsp: Replicator.UpdateResponse[PNCounter]) extends InternalCommand
  private final case class InternalGetValue(rsp: Replicator.GetResponse[PNCounter], replyTo: ActorRef[Int]) extends InternalCommand

  def apply(key: PNCounterKey): Behavior[Command] = Behaviors.setup { ctx =>
    implicit val node = DistributedData(ctx.system).selfUniqueAddress

    val behavior = DistributedData.withReplicatorMessageAdapter[Command, PNCounter] { replicator =>
      // External Command
      Behaviors.receiveMessage {
        case Increment(value) =>
          replicator.askUpdate(
            replyTo => Replicator.Update(key, PNCounter.empty, Replicator.WriteLocal, replyTo)(_.increment(value)),
            InternalUpdate.apply)
          Behaviors.same

        case Decrement(value) =>
          replicator.askUpdate(
            replyTo => Replicator.Update(key, PNCounter.empty, Replicator.WriteLocal, replyTo)(_.decrement(value)),
            InternalUpdate.apply)
          Behaviors.same

        case GetValue(replyTo) =>
          replicator.askGet(
            replyTo => Replicator.Get(key, Replicator.ReadLocal, replyTo),
            value => InternalGetValue(value, replyTo))
          Behaviors.same

        // Internal Command
        case internal: InternalCommand => internal match {
          case InternalUpdate(_) => Behaviors.same
          case InternalGetValue(rsp@Replicator.GetSuccess(`key`), replyTo) =>
            replyTo ! rsp.get(key).value.intValue
            Behaviors.same
          case InternalGetValue(_, _) => Behaviors.unhandled
        }
      }
    }
    // initialize value of counter to 0
    ctx.self ! Increment(0)
    behavior
  }
}


/**
 * Test case
 */
class PNCounterCacheSpec extends ScalaTestWithActorTestKit(singleClusterConf) with AnyWordSpecLike with LogCapturing {

  "PNCounterCache" should {

    "test1" in {
      val counter = spawn(PNGCounterCache(PNCounterKey("counter-1")))

      eventually {
        val probe = TestProbe[Int]
        counter ! GetValue(probe.ref)
        probe.expectMessage(0)
      }
      counter ! Increment(10)
      counter ! Decrement(5)

      eventually {
        val probe = TestProbe[Int]
        counter ! GetValue(probe.ref)
        probe.expectMessage(5)
      }
    }

  }

}
