package com.github.al.assad.akka.cluster.c_sharding

import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityTypeKey}
import akka.util.Timeout
import com.github.al.assad.akka.cluster.a_base.waitSystemUp
import com.github.al.assad.akka.cluster.c_sharding.ClusterShardingTest.Dotter
import com.github.al.assad.akka.sleep
import com.typesafe.config.ConfigFactory

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.{Failure, Success}

/**
 * Akka cluster sharding init with node role sample.
 * https://doc.akka.io/docs/akka/current/typed/cluster-sharding.html
 */
object DotterShardRegionWithRoleGuardian {
  sealed trait Command
  final case class Dot(regionId: String) extends Command
  final case class Get(regionId: String, replyTo: ActorRef[Int]) extends Command
  final case class GetRegionAddress(regionId: String, replyTo: ActorRef[String]) extends Command

  def apply(): Behavior[Command] = Behaviors.setup { ctx =>
    // init sharding
    val sharding = ClusterSharding(ctx.system)
    val dotterTypekey = EntityTypeKey[Dotter.Command]("Dotter")

    // init shard region with node-role "dotter"
    val dotterShardRegion: ActorRef[ShardingEnvelope[Dotter.Command]] = sharding.init(
      Entity(dotterTypekey)(createBehavior = entityContext => Dotter(entityContext.entityId))
        .withRole("dotter"))

    Behaviors.receiveMessage[Command] {
      case Dot(regionId) =>
        dotterShardRegion ! ShardingEnvelope(regionId, Dotter.Dot)
        Behaviors.same
      case Get(regionId, replyTo) =>
        dotterShardRegion ! ShardingEnvelope(regionId, Dotter.Get(replyTo))
        Behaviors.same
      case GetRegionAddress(regionId, replyTo) =>
        dotterShardRegion ! ShardingEnvelope(regionId, Dotter.GetAddress(replyTo))
        Behaviors.same
    }
  }
}

trait ClusterShardingNodeRuleTest {

  def launch(port: Int, role: String): ActorSystem[DotterShardRegionWithRoleGuardian.Command] = {
    val config = ConfigFactory
      .parseString(
        s"""akka.remote.artery.canonical.port = $port
           |akka.cluster.roles = [$role]""".stripMargin)
      .withFallback(ConfigFactory.load("cluster-base"))
    ActorSystem[DotterShardRegionWithRoleGuardian.Command](DotterShardRegionWithRoleGuardian(), "ClusterSystem", config)
  }

  implicit val timeout: Timeout = 20.seconds
}


object ClusterShardingNodeRuleTest1 extends App with ClusterShardingNodeRuleTest {

  import DotterShardRegionWithRoleGuardian._

  val system1 = launch(25251, "normal")
  val system2 = launch(25252, "dotter")
  val system3 = launch(25253, "dotter")
  waitSystemUp(system1, system2, system3).printState

  system1 ! Dot("d-1")
  system1 ! Dot("d-2")
  system2 ! Dot("d-3")
  system3 ! Dot("d-4")
  system3 ! Dot("d-2")

  // get address of region
  implicit val ec = system1.scheduler
  def printGetRegionAddress: String => _ = { regionId =>
    system1 ? (ref => GetRegionAddress(regionId, ref)) onComplete {
      case Success(addr) => println(s"@Message region $regionId address: $addr")
      case Failure(e) => throw e
    }
  }
  sleep(5.seconds)
  printGetRegionAddress("d-1")
  printGetRegionAddress("d-2")
  printGetRegionAddress("d-3")
  printGetRegionAddress("d-4")


  // get value of region
  def printGetRegionValue: String => _ = { regionId =>
    system1 ? (ref => Get(regionId, ref)) onComplete {
      case Success(addr) => println(s"@Message region $regionId => $addr")
      case Failure(e) => throw e
    }
  }
  printGetRegionValue("d-1")
  printGetRegionValue("d-2")
  printGetRegionValue("d-3")
  printGetRegionValue("d-4")
}
