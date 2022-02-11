package com.github.al.assad.akka.cluster.ddata

import akka.actor.testkit.typed.scaladsl.{LogCapturing, ScalaTestWithActorTestKit}
import akka.actor.typed.scaladsl.AskPattern.{Askable, schedulerFromActorSystem}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.ddata.typed.scaladsl.{DistributedData, Replicator}
import akka.cluster.ddata.{GCounter, GCounterKey, SelfUniqueAddress}
import com.github.al.assad.akka.cluster.ddata.GCounterCache.{GetValue, Increment}
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.DurationInt

/**
 * Akka Distributed Data GCount Demo.
 * GCounter is a “grow only counter”. It only supports increments, no decrements.
 *
 * https://doc.akka.io/docs/akka/current/typed/distributed-data.html#counters
 */
object GCounterCache {

  sealed trait Command
  // increment the counter
  case object Increment extends Command
  case class Increment(value: Int) extends Command
  // get the Counter value from Replicator
  final case class GetValue(replyTo: ActorRef[Int]) extends Command
  // get the Counter value from local cached
  final case class GetCachedValue(replyTo: ActorRef[Int]) extends Command

  // internal interaction protocol with Replicator[GCounter]
  private sealed trait InternalCommand extends Command
  private final case class InternalUpdate(rsp: Replicator.UpdateResponse[GCounter]) extends InternalCommand
  private final case class InternalGet(rsp: Replicator.GetResponse[GCounter], replyTo: ActorRef[Int]) extends InternalCommand
  private final case class InternalSubscribe(rsp: Replicator.SubscribeResponse[GCounter]) extends InternalCommand


  def apply(key: GCounterKey): Behavior[Command] = Behaviors.setup[Command] { context =>
    // self unique address
    implicit val node: SelfUniqueAddress = DistributedData(context.system).selfUniqueAddress
    /*
    Use ReplicatorMessageAdapter to handle the response from replicator.
    There is another to interact with Replicator by using ActorRef[Replicator.Command] from DistributedData(ctx.system).replicator,
    but it's more convenient to use the ReplicatorMessageAdapter.
    */
    DistributedData.withReplicatorMessageAdapter[Command, GCounter] { replicatorAdapter =>
      // subscribe to changes of the given `key`
      replicatorAdapter.subscribe(key, InternalSubscribe.apply)

      def updated(cachedValue: Int): Behavior[Command] = Behaviors.receiveMessage[Command] {
        // Outer Command
        case Increment =>
          replicatorAdapter.askUpdate(
            replyTo => Replicator.Update(key, GCounter.empty, Replicator.WriteLocal, replyTo)(_ :+ 1),
            InternalUpdate.apply)
          Behaviors.same

        case Increment(value) =>
          replicatorAdapter.askUpdate(
            replyTo => Replicator.Update(key, GCounter.empty, Replicator.WriteLocal, replyTo)(_ :+ value),
            InternalUpdate.apply)
          Behaviors.same

        case GetValue(replyTo) =>
          replicatorAdapter.askGet(
            replyTo => Replicator.Get(key, Replicator.ReadLocal, replyTo),
            value => InternalGet(value, replyTo))
          Behaviors.same

        case GetCachedValue(replyTo) =>
          replyTo ! cachedValue
          Behaviors.same

        // Internal Command
        case internal: InternalCommand => internal match {
          case InternalUpdate(rsp) => Behaviors.same

          case InternalGet(rsp@Replicator.GetSuccess(`key`), replyTo) =>
            val value = rsp.get(key).value.toInt
            replyTo ! value
            Behaviors.same

          case InternalGet(_, _) => Behaviors.unhandled

          case InternalSubscribe(rsp@Replicator.Changed(`key`)) =>
            val value = rsp.get(key).value.toInt
            updated(value)

          case InternalSubscribe(_) => Behaviors.unhandled
        }
      }

      updated(cachedValue = 0)
    }
  }
}


/**
 * Test case
 */
class GCounterCacheSpec extends ScalaTestWithActorTestKit(singleClusterConf) with AnyWordSpecLike with LogCapturing {

  "GCounterCache" should {

    "test1" in {
      val counter = spawn(GCounterCache(GCounterKey("counter-1")))
      counter ! Increment
      counter ! Increment(10)
      val probe = createTestProbe[Int]
      counter ! GetValue(probe.ref)
      probe.expectMessage(11)
    }

    "test2" in {
      val counter = spawn(GCounterCache(GCounterKey("counter-2")))
      val probe = createTestProbe[Int]
      counter ! Increment
      counter ! Increment
      eventually {
        counter ! GetValue(probe.ref)
        probe.expectMessage(2)
      }
    }

    "test3" in {
      val counter = spawn(GCounterCache(GCounterKey("counter-3")))
      counter ! Increment
      counter ! Increment
      eventually {
        val probe = createTestProbe[Int]
        counter ! GetValue(probe.ref)
        // get result from TestProbe
        val value = probe.receiveMessage()
        println(value)
      }
    }

    "test4 -> call outside of the Actor" in {
      val counter = spawn(GCounterCache(GCounterKey("counter-4")))
      counter ! Increment
      counter ! Increment
      val f: Future[Int] = counter ? (ref => GetValue(ref))
      val value = Await.result(f, 3.seconds)
      println(value)
    }
  }

}



