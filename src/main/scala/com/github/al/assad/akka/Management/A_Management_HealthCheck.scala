package com.github.al.assad.akka.Management

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior}
import akka.cluster.typed.{Cluster, Join}
import akka.management.scaladsl.AkkaManagement
import com.github.al.assad.akka.Cluster.a_base.{ClusterListener, waitSystemUp}
import com.typesafe.config.{Config, ConfigFactory}

/**
 * Use case of akka-management server and health check api
 *
 * https://doc.akka.io/docs/akka-management/current/akka-management.html
 */
trait AkkaManagementHealthCheckSample {

  object RootBehavior {
    def apply(): Behavior[Nothing] = Behaviors.setup[Nothing] { ctx =>
      ctx.spawn(ClusterListener(), "ClusterListener")
      Behaviors.empty
    }
  }

  // akka-management config
  def config(remotePort: Int, managementPort: Int): Config = ConfigFactory.parseString(
    s"""
       |akka.remote.artery.canonical.port=$remotePort
       |akka.management {
       |  http {
       |    hostname = "127.0.0.1"        // hostname for the akka-management http server
       |    port = $managementPort        // port for the akka-management http server
       |    base-path = "mycluster"       // base path of akka-management api
       |  }
       |  health-checks {
       |    readiness-path = "health/ready"   // health readiness path
       |    liveness-path = "health/alive"    // health liveness path
       |  }
       |}
       |""".stripMargin)
    .withFallback(ConfigFactory.load("cluster-base"))

  // disable health-check route config
  def disableHealthCheckConfig(remotePort: Int, managementPort: Int): Config = ConfigFactory.parseString(
    """akka.management.http.routes {
      |  health-checks = ""
      |}""".stripMargin)
    .withFallback(config(remotePort, managementPort))

  def launch(config: Config): ActorSystem[Nothing] = {
    val system = ActorSystem[Nothing](RootBehavior(), "ClusterSystem", config)
    // enable akka-management endpoint
    AkkaManagement(system).start()
    system
  }

}


/**
 * launch the akka system
 */
object AkkaManagementHealthCheckTest extends App with AkkaManagementHealthCheckSample {

  val system1 = launch(config(25251, 8558))
  val system2 = launch(config(25252, 8559))
  val system3 = launch(disableHealthCheckConfig(25253, 8560))

  waitSystemUp(system1, system2, system3).printState
  Cluster(system1).manager ! Join(system3.address)
}

