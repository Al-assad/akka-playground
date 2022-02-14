package com.github.al.assad.akka.cluster.b_ddata

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.ddata.typed.scaladsl.{DistributedData, Replicator}
import akka.cluster.ddata.{ORMultiMap, ORMultiMapKey}
import akka.util.Timeout
import com.github.al.assad.akka.TestImplicit.TestProbeWrapper
import com.github.al.assad.akka.cluster.b_ddata.ORMultiMapCache.{ContainsKey, ContainsValueOnKey, GetAll, GetValue, PutValue, RemoveAllByKey, RemoveValueByKey}
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration.DurationInt

/**
 * ORMultiMap (observed-remove multi-map) is a multi-map implementation that wraps an ORMap with an ORSet for the map’s value.
 */
//noinspection DuplicatedCode
object ORMultiMapCache {

  type UpdateResponse = Replicator.UpdateResponse[ORMultiMap[String, String]]
  type GetResponse = Replicator.GetResponse[ORMultiMap[String, String]]

  sealed trait Command
  final case class PutValue(key: String, value: Set[String]) extends Command
  final case class AppendValueToKey(key: String, value: String) extends Command
  final case class RemoveValueByKey(key: String, value: String) extends Command
  final case class RemoveAllByKey(key: String) extends Command

  final case class GetValue(key: String, replyTo: ActorRef[Set[String]]) extends Command
  final case class GetAll(replyTo: ActorRef[Map[String, Set[String]]]) extends Command
  final case class ContainsKey(key: String, replyTo: ActorRef[Boolean]) extends Command
  final case class ContainsValueOnKey(key: String, value: String, replyTo: ActorRef[Boolean]) extends Command

  sealed trait InternalCommand extends Command
  private final case class InternalUpdate(rsp: UpdateResponse) extends InternalCommand
  private final case class InternalGetValue(rsp: GetResponse, key: String, replyTo: ActorRef[Set[String]]) extends InternalCommand
  private final case class InternalGetAll(rsp: GetResponse, replyTo: ActorRef[Map[String, Set[String]]]) extends InternalCommand
  private final case class InternalContainsKey(rsp: GetResponse, key: String, replyTo: ActorRef[Boolean]) extends InternalCommand
  private final case class InternalContainsValueOnKey(rsp: GetResponse, key: String, value: String, replyTo: ActorRef[Boolean]) extends InternalCommand

  def apply(cacheId: String): Behavior[Command] = Behaviors.setup { ctx =>

    implicit val node = DistributedData(ctx.system).selfUniqueAddress
    implicit val timeout: Timeout = 2.seconds
    val cacheKey = ORMultiMapKey[String, String](cacheId)

    DistributedData.withReplicatorMessageAdapter[Command, ORMultiMap[String, String]] { replicator =>
      Behaviors.receiveMessage {

        case PutValue(key, value) =>
          replicator.askUpdate(
            replyTo => Replicator.Update(cacheKey, ORMultiMap.empty[String, String], Replicator.WriteLocal, replyTo)(_ :+ (key -> value)),
            InternalUpdate.apply)
          Behaviors.same

        case AppendValueToKey(key, value) =>
          replicator.askUpdate(
            replyTo => Replicator.Update(cacheKey, ORMultiMap.empty[String, String], Replicator.WriteLocal, replyTo)(_.addBindingBy(key, value)),
            InternalUpdate.apply)
          Behaviors.same

        case RemoveValueByKey(key, value) =>
          replicator.askUpdate(
            replyTo => Replicator.Update(cacheKey, ORMultiMap.empty[String, String], Replicator.WriteLocal, replyTo)(_.removeBindingBy(key, value)),
            InternalUpdate.apply)
          Behaviors.same

        case RemoveAllByKey(key) =>
          replicator.askUpdate(
            replyTo => Replicator.Update(cacheKey, ORMultiMap.empty[String, String], Replicator.WriteLocal, replyTo)(_.remove(key)),
            InternalUpdate.apply)
          Behaviors.same

        case GetValue(key, replyTo) =>
          replicator.askGet(
            replyTo => Replicator.Get(cacheKey, Replicator.ReadLocal, replyTo),
            rsp => InternalGetValue(rsp, key, replyTo))
          Behaviors.same

        case GetAll(replyTo) =>
          replicator.askGet(
            replyTo => Replicator.Get(cacheKey, Replicator.ReadLocal, replyTo),
            rsp => InternalGetAll(rsp, replyTo))
          Behaviors.same

        case ContainsKey(key, replyTo) =>
          replicator.askGet(
            replyTo => Replicator.Get(cacheKey, Replicator.ReadLocal, replyTo),
            rsp => InternalContainsKey(rsp, key, replyTo))
          Behaviors.same

        case ContainsValueOnKey(key, value, replyTo) =>
          replicator.askGet(
            replyTo => Replicator.Get(cacheKey, Replicator.ReadLocal, replyTo),
            rsp => InternalContainsValueOnKey(rsp, key, value, replyTo))
          Behaviors.same

        case internal: InternalCommand => internal match {
          case InternalUpdate(_@Replicator.UpdateSuccess(_)) =>
            Behaviors.same

          case InternalGetValue(rsp@Replicator.GetSuccess(id), key, replyTo) =>
            val result = rsp.get(id).get(key) match {
              case Some(value) => value
              case None => Set.empty[String]
            }
            replyTo ! result
            Behaviors.same

          case InternalGetAll(rsp@Replicator.GetSuccess(id), replyTo) =>
            replyTo ! rsp.get(id).entries
            Behaviors.same

          case InternalContainsKey(rsp@Replicator.GetSuccess(id), key, replyTo) =>
            replyTo ! rsp.get(id).contains(key)
            Behaviors.same

          case InternalContainsValueOnKey(rsp@Replicator.GetSuccess(id), key, value, replyTo) =>
            val result = rsp.get(id).get(key) match {
              case Some(values) => values.contains(value)
              case None => false
            }
            replyTo ! result
            Behaviors.same

          case _ => Behaviors.unhandled
        }
      }
    }
  }

}


class ORMultiMapCacheSpec extends ScalaTestWithActorTestKit(singleClusterConf) with AnyWordSpecLike {

  "ORMultiMapCache" should {
    "test1" in {
      val set = spawn(ORMultiMapCache("map-cache-1"))
      set ! PutValue("Alabama", Set("Birmingham", "Montgomery", "Mobile"))
      set ! PutValue("Alaska", Set("Anchorage", "Fairbanks"))
      set ! PutValue("Arizona", Set("Phoenix", "Tucson"))

      createTestProbe[Map[String, Set[String]]] to { probe =>
        set ! GetAll(probe.ref)
        println(probe.receiveMessage())
      }

      createTestProbe[Set[String]] to { probe =>
        set ! GetValue("Alabama", probe.ref)
        probe.receiveMessage() shouldBe Set("Birmingham", "Montgomery", "Mobile")
      }

      createTestProbe[Set[String]] to { probe =>
        set ! GetValue("California", probe.ref)
        probe.receiveMessage() shouldBe Set.empty[String]
      }

      createTestProbe[Boolean] from (set ! ContainsKey("Alabama", _)) expectMessage true
      createTestProbe[Boolean] from (set ! ContainsValueOnKey("Alabama", "Mobile", _)) expectMessage true

      set ! RemoveValueByKey("Alabama", "Mobile")
      set ! RemoveAllByKey("Alaska")

      createTestProbe[Boolean] from (set ! ContainsKey("Alaska", _)) expectMessage false
      createTestProbe[Boolean] from (set ! ContainsValueOnKey("Alabama", "Mobile", _)) expectMessage false

    }
  }

}
