package com.github.al.assad.akka.Cluster.b_ddata

import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.ddata.{LWWRegister, LWWRegisterKey}
import akka.cluster.ddata.typed.scaladsl.{DistributedData, Replicator}
import akka.util.Timeout
import com.github.al.assad.akka.TestImplicit.TestProbeWrapper
import com.github.al.assad.akka.Cluster.singleClusterConf
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration.DurationInt

/*
* LWWRegister (last writer wins register) can hold any (serializable) value.
 */
object RegisterCache {

  sealed trait Command
  final case class SetValue(value: String) extends Command
  final case class GetValue(replyTo: ActorRef[String]) extends Command

  private sealed trait InternalCommand extends Command
  private final case class InternalUpdate(rsp: Replicator.UpdateResponse[LWWRegister[String]]) extends InternalCommand
  private final case class InternalGetValue(rsp: Replicator.GetResponse[LWWRegister[String]], replyTo: ActorRef[String]) extends InternalCommand


  def apply(id: String): Behavior[Command] = Behaviors.setup { ctx =>
    def log = ctx.log

    implicit val node = DistributedData(ctx.system).selfUniqueAddress
    implicit val timeout: Timeout = 10.seconds
    val cacheKey = LWWRegisterKey[String](id)

    DistributedData.withReplicatorMessageAdapter[Command, LWWRegister[String]] { replicator =>
      log.info("start")
      Behaviors.receiveMessage {

        case SetValue(value) =>
          replicator.askUpdate(
            Replicator.Update(cacheKey, LWWRegister.create(""), Replicator.WriteLocal)(_.withValueOf(value)),
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

class RegisterCacheSpec extends ScalaTestWithActorTestKit(singleClusterConf) with AnyWordSpecLike {

  import RegisterCache._

  "RegisterCache" should {
    "test1" in {
      val register = spawn(RegisterCache("register-1"))

      register ! SetValue("Tulzscha")
      TestProbe[String] from (register ! GetValue(_)) expectMessage "Tulzscha"

      register ! SetValue("Yidhra")
      register ! SetValue("Abhoth")
      TestProbe[String] from (register ! GetValue(_)) expectMessage "Abhoth"

    }
  }
}
