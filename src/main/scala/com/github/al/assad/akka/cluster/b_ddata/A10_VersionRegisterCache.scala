package com.github.al.assad.akka.cluster.b_ddata

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.ddata.typed.scaladsl.{DistributedData, Replicator}
import akka.cluster.ddata.{LWWRegister, LWWRegisterKey, SelfUniqueAddress}
import akka.util.Timeout
import com.github.al.assad.akka.STAkkaSpec
import com.github.al.assad.akka.cluster.singleClusterConf

import scala.concurrent.duration.DurationInt

//noinspection DuplicatedCode
/*
* LWWRegister (last writer wins register) can hold any (serializable) value.
* Instead of using timestamps based on System.currentTimeMillis() time
* it is possible to use a timestamp value based on something else.
 */
object VersionRegisterCache {

  sealed trait Command
  final case class SetValue(value: Record) extends Command
  final case class GetValue(replyTo: ActorRef[Record]) extends Command

  private sealed trait InternalCommand extends Command
  private final case class InternalUpdate(rsp: Replicator.UpdateResponse[LWWRegister[Record]]) extends InternalCommand
  private final case class InternalGetValue(rsp: Replicator.GetResponse[LWWRegister[Record]], replyTo: ActorRef[Record]) extends InternalCommand

  case class Record(version: Int, name: String)
  object Record {
    def default = Record(0, "")
  }
  // Using Record.version as LWWRegister Clock
  implicit private val recordClock = new LWWRegister.Clock[Record] {
    override def apply(currentTimestamp: Long, value: Record): Long = value.version
  }

  def apply(id: String): Behavior[Command] = Behaviors.setup { ctx =>
    implicit val node: SelfUniqueAddress = DistributedData(ctx.system).selfUniqueAddress
    implicit val timeout: Timeout = 10.seconds
    val cacheKey = LWWRegisterKey[Record](id)

    DistributedData.withReplicatorMessageAdapter[Command, LWWRegister[Record]] { replicator =>
      // init value
      replicator.askUpdate(
        Replicator.Update(cacheKey, LWWRegister.create(Record.default), Replicator.WriteLocal)(_ => LWWRegister.create(Record.default)),
        InternalUpdate.apply)

      Behaviors.receiveMessage {
        case SetValue(value) =>
          replicator.askUpdate(
            Replicator.Update(cacheKey, LWWRegister.create(Record.default), Replicator.WriteLocal)(_.withValue(node, value, recordClock)),
            InternalUpdate.apply)
          Behaviors.same

        case GetValue(replyTo) =>
          replicator.askGet(
            replyTo => Replicator.Get(cacheKey, Replicator.ReadLocal, replyTo),
            rsp => InternalGetValue(rsp, replyTo))
          Behaviors.same

        case InternalUpdate(rsp@Replicator.UpdateSuccess(id)) =>
          Behaviors.same

        case InternalGetValue(rsp@Replicator.GetSuccess(id), replyTo) =>
          replyTo ! rsp.get(id).value
          Behaviors.same

        case _ =>
          Behaviors.unhandled
      }
    }
  }

}

class VersionRegisterCacheSpec extends ScalaTestWithActorTestKit(singleClusterConf) with STAkkaSpec {

  import VersionRegisterCache._

  "RegisterCache" should {

    "test1" in {
      val cache = spawn(VersionRegisterCache("version-register-1"))
      probeRef[Record](cache ! GetValue(_)) expectMessage Record.default

      probe[Record] { tp =>
        cache ! SetValue(Record(1, "Tulzscha"))
        cache ! GetValue(tp.ref)
        tp.expectMessage(Record(1, "Tulzscha"))
      }

      probe[Record] { tp =>
        cache ! SetValue(Record(2, "Abhoth"))
        cache ! SetValue(Record(2, "Nyarlathotep"))
        cache ! GetValue(tp.ref)
        tp.expectMessage(Record(2, "Abhoth"))
      }

      probe[Record] { tp =>
        cache ! SetValue(Record(10, "Daoloth"))
        cache ! SetValue(Record(43, "Ghroth"))
        cache ! SetValue(Record(7, "Cthulhu"))
        cache ! GetValue(tp.ref)
        tp.receiveMessage.name shouldBe "Ghroth"
      }

    }
  }
}
