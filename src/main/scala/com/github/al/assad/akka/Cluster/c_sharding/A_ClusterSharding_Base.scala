package com.github.al.assad.akka.Cluster.c_sharding

import akka.actor.typed._
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.MemberStatus
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityTypeKey}
import akka.util.Timeout
import com.github.al.assad.akka.BehaviorLoggerImplicit.log
import com.github.al.assad.akka.Cluster.CborSerializable
import com.github.al.assad.akka.Cluster.a_base.{printMembers, waitSystemUnit, waitSystemUp}
import com.github.al.assad.akka.Cluster.c_sharding.ClusterShardingTest1.{printGetDefault, printGetDefaultRegionAddress}
import com.github.al.assad.akka.sleep
import com.typesafe.config.ConfigFactory

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

/**
 * Akka cluster sharding sample
 *
 * https://doc.akka.io/docs/akka/current/typed/cluster-sharding.html
 */
//noinspection DuplicatedCode
object ClusterShardingTest {

  object Dotter {
    sealed trait Command
    final case object Dot extends Command with CborSerializable
    final case class Get(replyTo: ActorRef[Int]) extends Command with CborSerializable
    final case class GetAddress(replyTo: ActorRef[String]) extends Command with CborSerializable

    def apply(entityId: String): Behavior[Command] = Behaviors.setup { implicit ctx =>
      log.info(s"@Dotter[${ctx.system.address}] setup")
      var value = 0
      Behaviors
        .receiveMessage[Command] {
          case Dot =>
            log.info(s"@Dotter receive Dot in Node[${ctx.system.address}]")
            value += 1
            Behaviors.same
          case Get(replyTo) =>
            log.info(s"@Dotter receive Get in Node[${ctx.system.address}]")
            replyTo ! value
            Behaviors.same
          case GetAddress(replyTo) =>
            replyTo ! ctx.system.address.toString
            Behaviors.same
        }
        .receiveSignal {
          case (context, PreRestart) =>
            log.info(s"@Dotter[${context.system.address}] restart")
            Behaviors.same
          case (context, PostStop) =>
            log.info(s"@Dotter[${context.system.address}] stopped")
            Behaviors.same
        }
    }
  }


  object DotterShardRegionGuardian {
    sealed trait Command
    final case class Dot(regionId: String) extends Command
    final case class Get(regionId: String, replyTo: ActorRef[Int]) extends Command
    final case class GetRegionAddress(regionId: String, replyTo: ActorRef[String]) extends Command

    final case object DotDefault extends Command
    final case class GetDefault(replyTo: ActorRef[Int]) extends Command
    final case class GetDefaultRegionAddress(replyTo: ActorRef[String]) extends Command

    def apply(): Behavior[Command] = Behaviors.setup { ctx =>
      // init sharding
      val sharding = ClusterSharding(ctx.system)
      val dotterTypekey = EntityTypeKey[Dotter.Command]("Dotter")
      val dotterShardRegion: ActorRef[ShardingEnvelope[Dotter.Command]] = sharding.init(
        Entity(dotterTypekey)(createBehavior = entityContext => Dotter(entityContext.entityId))
      )
      // default region to demonstrate requesting a shard region via EntityRef
      val dotterDefault = sharding.entityRefFor(dotterTypekey, "dotter-default")

      Behaviors.receiveMessage[Command] {
        case DotDefault =>
          dotterDefault ! Dotter.Dot
          Behaviors.same
        case GetDefault(replyTo) =>
          dotterDefault ! Dotter.Get(replyTo)
          Behaviors.same
        case GetDefaultRegionAddress(replyTo) =>
          dotterDefault ! Dotter.GetAddress(replyTo)
          Behaviors.same

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
}


trait ClusterShardingTest {

  import ClusterShardingTest._

  def launch(port: Int): ActorSystem[DotterShardRegionGuardian.Command] = {
    val config = ConfigFactory
      .parseString(s"""akka.remote.artery.canonical.port= $port""".stripMargin)
      .withFallback(ConfigFactory.load("cluster-base"))
    ActorSystem[DotterShardRegionGuardian.Command](DotterShardRegionGuardian(), "ClusterSystem", config)
  }

  implicit val timeout: Timeout = 20.seconds
}


import com.github.al.assad.akka.Cluster.c_sharding.ClusterShardingTest.DotterShardRegionGuardian._

/**
 * Testing send message to cluster default sharding region
 */
object ClusterShardingTest1 extends App with ClusterShardingTest {
  val (system1, system2, system3) = (launch(25251), launch(25252), launch(25253))
  waitSystemUp(system1, system2, system3).printState

  implicit val ec = system1.scheduler

  // tell to default region
  println("@Action system1 ! DotDefault")
  println("@Action system2 ! GetDefault")
  println("@Action system3 ! GetDefault")
  system1 ! DotDefault
  system2 ! DotDefault
  system3 ! DotDefault

  // get value from default region
  def printGetDefault: ActorSystem[Command] => Unit = { sys =>
    sys ? (ref => GetDefault(ref)) onComplete {
      case Success(value) => println(s"@Message ${sys.address} ? GetDefault => $value")
      case Failure(ex) => throw ex
    }
  }
  sleep(5.seconds)
  printGetDefault(system1) // 3
  printGetDefault(system2) // 3
  printGetDefault(system3) // 3

  // get address of default region
  def printGetDefaultRegionAddress: ActorSystem[Command] => Unit = { sys =>
    sys ? (ref => GetDefaultRegionAddress(ref)) onComplete {
      case Success(value) => println(s"@Message ${sys.address} ? GetDefaultRegionAddress => $value")
      case Failure(ex) => throw ex
    }
  }
  // located on the same node
  printGetDefaultRegionAddress(system1)
  printGetDefaultRegionAddress(system2)
  printGetDefaultRegionAddress(system3)
}


/**
 * Testing send message to custom cluster sharding region
 */
object ClusterShardingTest2 extends App with ClusterShardingTest {
  val (system1, system2, system3) = (launch(25251), launch(25252), launch(25253))
  waitSystemUp(system1, system2, system3).printState

  // Send Dot message to multiple sharding regions
  system1 ! Dot("dotter-a")
  system1 ! Dot("dotter-b")
  system2 ! Dot("dotter-b")
  system2 ! Dot("dotter-c")
  system3 ! Dot("dotter-c")

  implicit val ec = system1.scheduler
  def printGetRegionAddress: (String, ActorSystem[Command]) => _ = { (regionId, sys) =>
    sys ? (ref => GetRegionAddress(regionId, ref)) onComplete {
      case Success(value) => println(s"@Message ${sys.address} ? GetRegionAddress($regionId) => $value")
      case Failure(ex) => throw ex
    }
  }

  def printGet: (String, ActorSystem[Command]) => _ = { (regionId, sys) =>
    sys ? (ref => Get(regionId, ref)) onComplete {
      case Success(value) => println(s"@Message ${sys.address} ? Get($regionId) => $value")
      case Failure(ex) => throw ex
    }
  }

  // Get value from sharding regions
  sleep(3.seconds)
  printGet("dotter-a", system1) // 1
  printGet("dotter-b", system1) // 2
  printGet("dotter-c", system1) // 3

  // Get address of sharding regions
  printGetRegionAddress("dotter-a", system1)
  printGetRegionAddress("dotter-b", system1)
  printGetRegionAddress("dotter-c", system1)
}


/**
 * Testing failover of sharding region
 */
object ClusterShardingTest3 extends App with ClusterShardingTest {
  val (system1, system2, system3) = (launch(25251), launch(25252), launch(25253))
  waitSystemUp(system1, system2, system3).printState

  implicit val ec = system1.scheduler

  system1 ! DotDefault
  system2 ! DotDefault
  system3 ! DotDefault

  sleep(10.seconds)
  printGetDefault(system1)  // 3
  printGetDefaultRegionAddress(system1) // 2521

  // terminate system1
  system1.terminate()
  waitSystemUnit(MemberStatus.Removed, system1)

  sleep(20.seconds)
  printMembers(system2)
  system2 ! DotDefault
  printGetDefault(system2)  // 1
  printGetDefaultRegionAddress(system2)
}










