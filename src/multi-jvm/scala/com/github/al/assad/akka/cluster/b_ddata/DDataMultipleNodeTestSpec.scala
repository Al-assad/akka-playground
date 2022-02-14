package com.github.al.assad.akka.cluster.b_ddata

import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.adapter.ClassicActorSystemOps
import akka.cluster.ddata.Replicator
import akka.cluster.ddata.Replicator.ReplicaCount
import akka.cluster.ddata.typed.scaladsl.DistributedData
import akka.cluster.ddata.typed.scaladsl.Replicator.GetReplicaCount
import akka.cluster.typed.{Cluster, Join}
import akka.remote.testconductor.RoleName
import akka.remote.testkit.{MultiNodeConfig, MultiNodeSpec}
import com.github.al.assad.akka.cluster.STMultiNodeSpec
import com.github.al.assad.akka.cluster.b_ddata.DDataMultipleNodeTestSpecConfig.{node1, node2, node3}
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps


/**
 * run all tests: sbt multi-jvm:test
 * run current tests: sbt multi-jvm:testOnly com.github.al.assad.akka.cluster.b_ddata.DDataMultipleNodeTestSpec
 */

// multiple node config
object DDataMultipleNodeTestSpecConfig extends MultiNodeConfig {
  val node1 = role("node-1")
  val node2 = role("node-2")
  val node3 = role("node-3")

  commonConfig(ConfigFactory.parseString(
    """akka.loglevel = INFO
      |akka.actor.provider = "cluster"
      |akka.log-dead-letters-during-shutdown = off
      |""".stripMargin))
}

// {TestName}MultiJvm{NodeName}
class DDataMultipleNodeTestSpecMultiJvmNode1 extends DDataMultipleNodeTestSpec
class DDataMultipleNodeTestSpecMultiJvmNode2 extends DDataMultipleNodeTestSpec
class DDataMultipleNodeTestSpecMultiJvmNode3 extends DDataMultipleNodeTestSpec

/**
 * https://doc.akka.io/docs/akka/current/multi-node-testing.html
 */
class DDataMultipleNodeTestSpec extends MultiNodeSpec(DDataMultipleNodeTestSpecConfig) with STMultiNodeSpec {

  override def initialParticipants: Int = roles.size

  implicit val typedSystem: ActorSystem[Nothing] = system.toTyped
  val cluster = Cluster(typedSystem)

  def join(from: RoleName, to: RoleName): Unit = {
    runOn(from) {
      cluster.manager ! Join(node(to).address)
    }
    enterBarrier(from.name + "-joined")
  }

  "YaCounterCache on Multiple JVM" should {

    "join cluster" in within(20.seconds) {
      join(node1, node1)
      join(node2, node1)
      join(node3, node1)

      awaitAssert {
        val probe = TestProbe[ReplicaCount]
        DistributedData(typedSystem).replicator ! GetReplicaCount(probe.ref)
        probe.expectMessage(Replicator.ReplicaCount(roles.size))
      }
      enterBarrier("after-1")
    }

    "replicate cached entry" in within(10.seconds) {
      val counterCache = system.spawnAnonymous(YaCounterCache("counter-1"))
      runOn(node1) {
        counterCache ! YaCounterCache.Increment()
      }
      awaitAssert {
        val probe = TestProbe[Int]
        counterCache ! YaCounterCache.GetValue(probe.ref)
        probe.expectMessage(1)
      }
      enterBarrier("after-2")
    }

    "replicate many cached entries" in within(30.seconds) {
      val counterCache = system.spawnAnonymous(YaCounterCache("counter-2"))
      runOn(node1) {
        (1 to 50).foreach(_ => counterCache ! YaCounterCache.Increment())
      }
      awaitAssert {
        val probe = TestProbe[Int]
        counterCache ! YaCounterCache.GetValue(probe.ref)
        probe.expectMessage(50)
      }
      enterBarrier("after-3")
    }

    "replicate cached entry on different node" in within(10.seconds) {
      val counterCache = system.spawnAnonymous(YaCounterCache("counter-3"))
      runOn(node1) {
        counterCache ! YaCounterCache.Increment()
      }
      runOn(node2) {
        counterCache ! YaCounterCache.Increment()
      }
      runOn(node3) {
        counterCache ! YaCounterCache.Increment()
      }
      awaitAssert {
        val probe = TestProbe[Int]
        counterCache ! YaCounterCache.GetValue(probe.ref)
        probe.expectMessage(3)
      }
      enterBarrier("after-4")
    }

  }
}


