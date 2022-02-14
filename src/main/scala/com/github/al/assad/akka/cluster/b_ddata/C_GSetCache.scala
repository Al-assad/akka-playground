package com.github.al.assad.akka.cluster.b_ddata

import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.ddata.typed.scaladsl.{DistributedData, Replicator}
import akka.cluster.ddata.{GSet, GSetKey}
import akka.util.Timeout
import com.github.al.assad.akka.cluster.b_ddata.GSetCache.{Add, Contains, GetElements, Size}
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

object GSetCache {

  sealed trait Command
  final case class Add(value: String) extends Command
  final case class Contains(value: String, replyTo: ActorRef[Boolean]) extends Command
  final case class Size(replyTo: ActorRef[Int]) extends Command
  final case class GetElements(replyTo: ActorRef[Set[String]]) extends Command

  sealed trait InternalCommand extends Command
  private final case object Reset extends InternalCommand
  private final case class InternalUpdate(rsp: Replicator.UpdateResponse[GSet[String]]) extends InternalCommand
  private final case class InternalGetSize(rsp: Replicator.GetResponse[GSet[String]], replyTo: ActorRef[Int]) extends InternalCommand
  private final case class InternalGetContains(rsp: Replicator.GetResponse[GSet[String]], value: String, replyTo: ActorRef[Boolean]) extends InternalCommand
  private final case class InternalGetValues(rsp: Replicator.GetResponse[GSet[String]], replyTo: ActorRef[Set[String]]) extends InternalCommand


  def apply(cacheKey: GSetKey[String]): Behavior[Command] = Behaviors.setup { ctx =>
    implicit val node = DistributedData(ctx.system).selfUniqueAddress
    implicit val timeout: Timeout = 2.seconds

    def behavior = DistributedData.withReplicatorMessageAdapter[Command, GSet[String]] { replicator =>
      Behaviors.receiveMessage {
        // External Command
        case Add(value) =>
          replicator.askUpdate(
            replyTo => Replicator.Update(cacheKey, GSet.empty[String], Replicator.WriteLocal, replyTo)(_ + value),
            InternalUpdate.apply)
          Behaviors.same

        case Size(replyTo) =>
          replicator.askGet(
            replyTo => Replicator.Get(cacheKey, Replicator.ReadLocal, replyTo),
            rsp => InternalGetSize(rsp, replyTo))
          Behaviors.same

        case Contains(value, replyTo) =>
          replicator.askGet(
            replyTo => Replicator.Get(cacheKey, Replicator.ReadLocal, replyTo),
            rsp => InternalGetContains(rsp, value, replyTo))
          Behaviors.same

        case GetElements(replyTo) =>
          replicator.askGet(
            replyTo => Replicator.Get(cacheKey, Replicator.ReadLocal, replyTo),
            rsp => InternalGetValues(rsp, replyTo))
          Behaviors.same

        case Reset =>
          replicator.askUpdate(
            replyTo => Replicator.Update(cacheKey, GSet.empty[String], Replicator.WriteLocal, replyTo)(_ => GSet.empty[String]),
            InternalUpdate.apply)
          Behaviors.same

        // Internal Command
        case internal: InternalCommand => internal match {
          case InternalUpdate(_) => Behaviors.same

          case InternalGetSize(rsp@Replicator.GetSuccess(key), replyTo) =>
            replyTo ! rsp.get(key).size
            Behaviors.same
          case InternalGetSize(_, _) => Behaviors.unhandled

          case InternalGetContains(rsp@Replicator.GetSuccess(key), value, replyTo) =>
            replyTo ! rsp.get(key).contains(value)
            Behaviors.same
          case InternalGetContains(_, _, _) => Behaviors.unhandled

          case InternalGetValues(rsp@Replicator.GetSuccess(key), replyTo) =>
            replyTo ! rsp.get(key).elements
            Behaviors.same
          case InternalGetValues(_, _) => Behaviors.unhandled
        }
      }
    }
    // force reset when start, off course the initial command can decided within the spawn behavior.
    ctx.self ! Reset
    behavior
  }
}

class GSetCacheSpec extends ScalaTestWithActorTestKit(singleClusterConf) with AnyWordSpecLike {

  "GSetCache" should {

    "test1" in {
      val set = spawn(GSetCache(GSetKey[String]("set-1")))
      val probe = createTestProbe[Int]
      set ! Size(probe.ref)
      println(probe.receiveMessage())
    }

    "test2" in {
      val set = spawn(GSetCache(GSetKey[String]("set-2")))
      set ! Add("a")
      set ! Add("b")
      set ! Add("c")
      set ! Add("d")
      eventually {
        val probe = TestProbe[Int]
        set ! Size(probe.ref)
        println(probe.receiveMessage())
      }
      eventually {
        val probe = TestProbe[Boolean]
        set ! Contains("a", probe.ref)
        println(probe.receiveMessage())
      }
      eventually {
        val probe = TestProbe[Set[String]]
        set ! GetElements(probe.ref)
        println(probe.receiveMessage())
      }
    }

  }
}
