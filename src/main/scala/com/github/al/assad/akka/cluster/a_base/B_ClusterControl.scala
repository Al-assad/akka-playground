package com.github.al.assad.akka.cluster.a_base

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior}
import akka.cluster.MemberStatus
import akka.cluster.typed.{Cluster, Join, Leave, PrepareForFullClusterShutdown}
import com.github.al.assad.akka.sleep
import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.duration.DurationInt

/**
 * Cluster Control sample
 */
//noinspection DuplicatedCode
trait ClusterControlTest {

  object RootBehavior {
    def apply(): Behavior[Nothing] = Behaviors.setup[Nothing] { ctx =>
      ctx.spawn(ClusterListener(), "ClusterListener")
      Behaviors.empty
    }
  }

  // launch akka system on specific port
  def launch(port: Int): (ActorSystem[Nothing], Cluster) = {
    val config = ConfigFactory
      .parseString(s"""akka.remote.artery.canonical.port=$port""".stripMargin)
      .withFallback(ConfigFactory.load("cluster-base"))

    val system = ActorSystem[Nothing](RootBehavior(), "ClusterSystem", config)
    val cluster = Cluster(system)
    system -> cluster
  }

  // launch akka system with specific config
  def launchWithConf(config: Config): (ActorSystem[Nothing], Cluster) = {
    val system = ActorSystem[Nothing](RootBehavior(), "ClusterSystem", config)
    val cluster = Cluster(system)
    system -> cluster
  }

}


/**
 * Test1: Wait unit all clusters becoming up state
 */
object ClusterControlTest1 extends App with ClusterControlTest {
  val (system1, cluster1) = launch(25251)
  val (system2, cluster2) = launch(25252)
  val (system3, cluster3) = launch(0)

  waitClusterUp(cluster1, cluster2, cluster3)
  println("@Message All cluster up")
  cluster1.state.members.foreach(s => println(s"@Message $s"))
}

/**
 * Test2: Leave from cluster and rejoin to it
 */
object ClusterControlLeaveTest extends App with ClusterControlTest{
  val (system1, cluster1) = launch(25251)
  val (system2, cluster2) = launch(25252)
  val (system3, cluster3) = launch(0)

  waitClusterUp(cluster1, cluster2, cluster3)
  println("@Message All cluster up")
  printMembers(cluster1)

  // level cluster2
  println("@Action Leave cluster2")
  cluster1.manager ! Leave(cluster2.selfMember.address)

  waitClusterUnit(MemberStatus.Removed, cluster2)
  println(s"@Message cluster2 is ${cluster2.selfMember.status}")
  printMembers(cluster1) // cluster2 is Exiting

  sleep(3.seconds)
  printMembers(cluster1) // cluster2 is Removed
  assert(cluster1.state.members.size == 2)

  // remove cluster2
  /* The actor system on a node that exited or was downed cannot join the cluster again. In particular,
     a node that was downed while being unreachable and then regains connectivity cannot rejoin the cluster.
     Instead, the process has to be restarted on the node, creating a new actor system that can go through
     the joining process again.
   */
  println("@Action Terminal cluster2")
  system2.terminate()
  println("@Action Relaunch cluster2")
  val (reSystem2, reCluster2) = launch(25252)

  // after relaunch cluster2, it will join to the akka cluster automatically
  waitClusterUp(reCluster2)
  assert(cluster1.state.members.size == 3)
  println("@Message cluster2 is Joined")
  printMembers(cluster1)
}


/**
 * Test3: Join to Cluster automatically via config
 */
object ClusterControlJoinAutomaticallyTest extends App with ClusterControlTest{
  val (system1, cluster1) = launch(25251)
  val (system2, cluster2) = launch(25252)

  waitClusterUp(cluster1, cluster2)
  println("@Message All cluster up")
  printMembers(cluster1)

  // launch cluster3 and join to the cluster
  // cluster3 will automatically join to the akka cluster via config
  val (system3, cluster3) = launch(25253)
  waitClusterUp(cluster3)
  println("@Message cluster3 is Up")

  assert(cluster1.state.members.size == 3)
  println("@Message cluster3 was Joined to the cluster automatically")
  printMembers(cluster1)
}


/**
 * Test4: Join to Cluster manually
 */
object ClusterControlJoinManuallyTest extends App with ClusterControlTest{
  val (system1, cluster1) = launch(25251)
  val (system2, cluster2) = launch(25252)

  waitClusterUp(cluster1, cluster2)
  println("@Message All cluster up")
  printMembers(cluster1)

  val (system3, cluster3) = launchWithConf(ConfigFactory.parseString(
    s"""akka.actor.provider = cluster
       |akka.remote.artery.canonical.port = 0""".stripMargin))

  // join cluster3 to the cluster of cluster1
  cluster3.manager ! Join(cluster1.selfMember.address)

  waitClusterUp(cluster3)
  println("@Message cluster3 is Up")
  assert(cluster1.state.members.size == 3)
  println("@Message cluster3 was Joined to the cluster")
  printMembers(cluster1)

}

/**
 * Test5: terminate cluster
 */

object ClusterControlShutdownAllTest extends App with ClusterControlTest{
  val (system1, cluster1) = launch(25251)
  val (system2, cluster2) = launch(25252)
  val (system3, cluster3) = launch(0)

  waitClusterUp(cluster1, cluster2)
  println("@Message All cluster up")

  // shutdown all node of cluster
  cluster1.manager ! PrepareForFullClusterShutdown

  waitClusterUnit(MemberStatus.ReadyForShutdown, cluster2)
  printMembers(cluster1)

  // shutdown cluster1, cluster2, cluster3
  system1.terminate()
  system2.terminate()
  system3.terminate()
}



