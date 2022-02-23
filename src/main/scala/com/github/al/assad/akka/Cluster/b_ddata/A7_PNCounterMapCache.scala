package com.github.al.assad.akka.Cluster.b_ddata

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.ddata.{PNCounterMap, PNCounterMapKey}
import akka.cluster.ddata.typed.scaladsl.{DistributedData, Replicator}
import akka.util.Timeout
import com.github.al.assad.akka.TestImplicit.TestProbeWrapper
import com.github.al.assad.akka.Cluster.b_ddata.PNCounterMapCache.{Decrement, GetAll, GetValue, Increment}
import com.github.al.assad.akka.Cluster.singleClusterConf
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration.DurationInt

//noinspection DuplicatedCode
object PNCounterMapCache {

  type UpdateResponse = Replicator.UpdateResponse[PNCounterMap[String]]
  type GetResponse = Replicator.GetResponse[PNCounterMap[String]]

  sealed trait Command
  final case class Increment(key: String, delta: Int = 1) extends Command
  final case class Decrement(key: String, delta: Int = 1) extends Command
  final case class GetValue(key: String, replyTo: ActorRef[Option[Int]]) extends Command
  final case class GetAll(replyTo: ActorRef[Map[String, Int]]) extends Command

  sealed trait InternalCommand extends Command
  private final case class InternalUpdate(rsp: UpdateResponse) extends InternalCommand
  private final case class InternalGetValue(rsp: GetResponse, key: String, replyTo: ActorRef[Option[Int]]) extends InternalCommand
  private final case class InternalGetAll(rsp: GetResponse, replyTo: ActorRef[Map[String, Int]]) extends InternalCommand

  def apply(cacheId: String): Behavior[Command] = Behaviors.setup { ctx =>

    implicit val node = DistributedData(ctx.system).selfUniqueAddress
    implicit val timeout: Timeout = 2.seconds
    val cacheKey = PNCounterMapKey[String](cacheId)

    DistributedData.withReplicatorMessageAdapter[Command, PNCounterMap[String]] { replicator =>
      Behaviors.receiveMessage {
        // External Command
        case Increment(key, delta) =>
          replicator.askUpdate(
            Replicator.Update(cacheKey, PNCounterMap.empty[String], Replicator.WriteLocal)(_.incrementBy(key, delta)),
            InternalUpdate.apply)
          Behaviors.same

        case Decrement(key, delta) =>
          replicator.askUpdate(
            Replicator.Update(cacheKey, PNCounterMap.empty[String], Replicator.WriteLocal)(_.decrementBy(key, delta)),
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

        // Internal Command
        case InternalUpdate(_@Replicator.UpdateSuccess(_)) =>
          Behaviors.same

        case InternalGetValue(rsp@Replicator.GetSuccess(id), key, replyTo) =>
          replyTo ! rsp.get(id).get(key).map(_.toInt)
          Behaviors.same

        case InternalGetAll(rsp@Replicator.GetSuccess(id), replyTo) =>
          replyTo ! rsp.get(id).entries.map { case (k, v) => k -> v.toInt }
          Behaviors.same
      }
    }
  }

}

class PNCounterMapCacheSpce extends ScalaTestWithActorTestKit(singleClusterConf) with AnyWordSpecLike {

  "PNCounterMapCache" should {

    "test1" in {
      val map = spawn(PNCounterMapCache("cache-1"))
      map ! Increment("Azathoth")
      map ! Increment("Nyarlathotep", 20)
      map ! Increment("Daoloth", 44)
      map ! Increment("Nyarlathotep")

      createTestProbe[Map[String, Int]] to { probe =>
        map ! GetAll(probe.ref)
        println(probe.receiveMessage())
      }

      createTestProbe[Option[Int]] from (map ! GetValue("Azathoth", _)) expectMessage Some(1)
      createTestProbe[Option[Int]] from (map ! GetValue("Tulzscha", _)) expectMessage None

      map ! Decrement("Daoloth", 14)
      createTestProbe[Option[Int]] from (map ! GetValue("Daoloth", _)) expectMessage Some(30)

      map ! Decrement("Yidhra", 10)
      createTestProbe[Option[Int]] from (map ! GetValue("Yidhra", _)) expectMessage Some(-10)

    }
  }
}
