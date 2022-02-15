package com.github.al.assad.akka.cluster.a_base

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior}
import akka.cluster.typed.{Cluster, Leave}
import com.github.al.assad.akka.cluster.a_base.ClusterControl.launch
import com.github.al.assad.akka.sleep
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration.DurationInt

/**
 * Cluster Control sample
 */
//noinspection DuplicatedCode
object ClusterControl {

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
}


/**
 * Test1: leave node from cluster
 */
object ClusterControlTest1 extends App {
  val (system1, cluster1) = launch(25251)
  val (system2, cluster2) = launch(25252)
  val (system3, cluster3) = launch(0)

  sleep(6.seconds)
  println("@Action leave cluster2")
  cluster1.manager ! Leave(cluster2.selfMember.address)
}


