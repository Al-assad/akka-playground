package com.github.al.assad.akka.Cluster.b_ddata

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.ddata.typed.scaladsl.Replicator.{GetResponse, UpdateResponse}
import akka.cluster.ddata.typed.scaladsl.{DistributedData, Replicator}
import akka.cluster.ddata.{LWWMap, LWWMapKey}
import akka.util.Timeout
import com.github.al.assad.akka.TestImplicit.TestProbeWrapper
import com.github.al.assad.akka.Cluster.b_ddata.LWWMapCache.{ContainsValue, GetAll, GetValue, PutValue, RemoveValue, Size, User}
import com.github.al.assad.akka.Cluster.singleClusterConf
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration.DurationInt

/**
 * LWWMap (last writer wins map) is a specialized ORMap with LWWRegister (last writer wins register) values.
 *
 */
object LWWMapCache {

  sealed trait Command
  final case class PutValue(key: String, value: User) extends Command
  final case class RemoveValue(key: String) extends Command

  final case class GetValue(key: String, replyTo: ActorRef[Option[User]]) extends Command
  final case class GetAll(replyTo: ActorRef[Map[String, User]]) extends Command
  final case class ContainsValue(key: String, replyTo: ActorRef[Boolean]) extends Command
  final case class Size(replyTo: ActorRef[Int]) extends Command

  sealed trait InternalCommand extends Command
  private final case class InternalUpdate(rsp: UpdateResponse[LWWMap[String, User]]) extends InternalCommand
  private final case class InternalGet(rsp: GetResponse[LWWMap[String, User]], key: String, replyTo: ActorRef[Option[User]]) extends InternalCommand
  private final case class InternalGetAll(rsp: GetResponse[LWWMap[String, User]], replyTo: ActorRef[Map[String, User]]) extends InternalCommand
  private final case class InternalContains(rsp: GetResponse[LWWMap[String, User]], key: String, replyTo: ActorRef[Boolean]) extends InternalCommand
  private final case class InternalSize(rsp: GetResponse[LWWMap[String, User]], replyTo: ActorRef[Int]) extends InternalCommand

  case class User(name: String, city: String)

  def apply[A](cacheId: String): Behavior[Command] = Behaviors.setup { ctx =>
    implicit val node = DistributedData(ctx.system).selfUniqueAddress
    implicit val timeout: Timeout = 2.seconds
    val cacheKey = LWWMapKey[String, User](cacheId)

    DistributedData.withReplicatorMessageAdapter[Command, LWWMap[String, User]] { replicator =>
      Behaviors.receiveMessage {
        // External command
        case PutValue(key, value) =>
          replicator.askUpdate(
            replyTo => Replicator.Update(cacheKey, LWWMap.empty[String, User], Replicator.WriteLocal, replyTo)(_ :+ (key -> value)),
            InternalUpdate.apply)
          Behaviors.same

        case RemoveValue(key) =>
          replicator.askUpdate(
            replyTo => Replicator.Update(cacheKey, LWWMap.empty[String, User], Replicator.WriteLocal, replyTo)(_.remove(node, key)),
            InternalUpdate.apply)
          Behaviors.same

        case GetValue(key, replyTo) =>
          replicator.askGet(
            replyTo => Replicator.Get(cacheKey, Replicator.ReadLocal, replyTo),
            rsp => InternalGet(rsp, key, replyTo))
          Behaviors.same

        case GetAll(replyTo) =>
          replicator.askGet(
            replyTo => Replicator.Get(cacheKey, Replicator.ReadLocal, replyTo),
            rsp => InternalGetAll(rsp, replyTo))
          Behaviors.same

        case ContainsValue(key, replyTo) =>
          replicator.askGet(
            replyTo => Replicator.Get(cacheKey, Replicator.ReadLocal, replyTo),
            rsp => InternalContains(rsp, key, replyTo))
          Behaviors.same

        case Size(replyTo) =>
          replicator.askGet(
            replyTo => Replicator.Get(cacheKey, Replicator.ReadLocal, replyTo),
            rsp => InternalSize(rsp, replyTo))
          Behaviors.same

        // Internal command
        case internal: InternalCommand => internal match {
          case InternalUpdate(_@Replicator.UpdateSuccess(_)) =>
            Behaviors.same

          case InternalGet(rsp@Replicator.GetSuccess(key), mapKey, replyTo) =>
            replyTo ! rsp.get(key).get(mapKey)
            Behaviors.same

          case InternalGetAll(rsp@Replicator.GetSuccess(key), replyTo) =>
            replyTo ! rsp.get(key).entries
            Behaviors.same

          case InternalContains(rsp@Replicator.GetSuccess(key), mapKey, replyTo) =>
            replyTo ! rsp.get(key).contains(mapKey)
            Behaviors.same

          case InternalSize(rsp@Replicator.GetSuccess(key), replyTo) =>
            replyTo ! rsp.get(key).size
            Behaviors.same

          case _ => Behaviors.unhandled
        }
      }
    }
  }
}

class LWWMapMapCacheSpec extends ScalaTestWithActorTestKit(singleClusterConf) with AnyWordSpecLike {

  "ORMapCache" should {

    "test1" in {
      val map = spawn(LWWMapCache("map-1"))
      map ! PutValue("144514", User("Alice", "London"))
      map ! PutValue("144515", User("Bob", "Paris"))
      map ! PutValue("144516", User("Charlie", "New York"))
      map ! PutValue("144517", User("David", "Madrid"))

      createTestProbe[Option[User]] to { probe =>
        map ! GetValue("144514", probe.ref)
        probe.expectMessage(Some(User("Alice", "London")))
      }

      createTestProbe[Int] to { probe =>
        map ! Size(probe.ref)
        probe.expectMessage(4)
      }

      createTestProbe[Map[String, User]] to { probe =>
        map ! GetAll(probe.ref)
        println(probe.receiveMessage())
      }

      map ! RemoveValue("144514")
      map ! RemoveValue("144517")

      createTestProbe[Boolean] from (map ! ContainsValue("144514", _)) expectMessage false
      createTestProbe[Boolean] from (map ! ContainsValue("144517", _)) expectMessage false
    }
  }

}
