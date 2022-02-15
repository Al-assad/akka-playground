package com.github.al.assad.akka.cluster.b_ddata

import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.ddata.typed.scaladsl.{DistributedData, Replicator}
import akka.cluster.ddata.{Flag, FlagKey}
import akka.util.Timeout
import com.github.al.assad.akka.TestImplicit.TestProbeWrapper
import com.github.al.assad.akka.cluster.singleClusterConf
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration.DurationInt

/**
 * Flag is a data type for a boolean value that is initialized to false and can be switched to true.
 * Thereafter it cannot be changed. true wins over false in merge.
 */
object FlagCache {
  sealed trait Command
  final case class Enabled(replyTo: ActorRef[Boolean]) extends Command
  final case object SwitchOn extends Command

  private sealed trait InternalCommand extends Command
  private final case class InternalUpdate(rsp: Replicator.UpdateResponse[Flag]) extends InternalCommand
  private final case class InternalGet(rsp: Replicator.GetResponse[Flag], replyTo: ActorRef[Boolean]) extends InternalCommand

  def apply(id: String): Behavior[Command] = Behaviors.setup { ctx =>
    implicit val node = DistributedData(ctx.system).selfUniqueAddress
    implicit val timeout: Timeout = 10.seconds
    val cacheKey = FlagKey(id)

    DistributedData.withReplicatorMessageAdapter[Command, Flag] { replicator =>
      // init cache
      replicator.askUpdate(Replicator.Update(cacheKey, Flag.Disabled, Replicator.WriteLocal)(_ => Flag.Disabled), InternalUpdate.apply)

      Behaviors.receiveMessage {
        case Enabled(replyTo) =>
          replicator.askGet(
            replTo => Replicator.Get(cacheKey, Replicator.ReadLocal, replTo),
            rsp => InternalGet(rsp, replyTo))
          Behaviors.same

        case SwitchOn =>
          replicator.askUpdate(
            Replicator.Update(cacheKey, Flag.Disabled, Replicator.WriteLocal)(_.switchOn),
            InternalUpdate.apply)
          Behaviors.same

        case InternalUpdate(rsp@Replicator.UpdateSuccess(id)) => Behaviors.same

        case InternalGet(rsp@Replicator.GetSuccess(id), replyTo) =>
          replyTo ! rsp.get(cacheKey).enabled
          Behaviors.same
      }
    }
  }

}

class FlagCacheSpec extends ScalaTestWithActorTestKit(singleClusterConf) with AnyWordSpecLike {

  import FlagCache._

  def assertFlag(assert: Boolean)(implicit flag: ActorRef[Command]) =
    TestProbe[Boolean] to { probe =>
      flag ! Enabled(probe.ref)
      probe.expectMessage(assert)
    }

  "FlagCache" should {

    "test1" in {
      implicit val flag = spawn(FlagCache("flag"))
      assertFlag(assert = false)
      flag ! SwitchOn
      assertFlag(assert = true)
      flag ! SwitchOn
      assertFlag(assert = true)
    }
  }
}
