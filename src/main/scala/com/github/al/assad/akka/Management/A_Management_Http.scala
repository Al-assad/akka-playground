package com.github.al.assad.akka.Management

import akka.NotUsed
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior}
import akka.cluster.typed.{Cluster, Join}
import akka.http.impl.settings.ServerSentEventSettingsImpl
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding.Get
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.unmarshalling.sse.EventStreamUnmarshalling
import akka.management.scaladsl.AkkaManagement
import akka.stream.scaladsl.Source
import com.github.al.assad.akka.Cluster.a_base.{ClusterListener, waitSystemUp}
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import sttp.client3.Response
import sttp.model.StatusCode.NotFound

import scala.language.postfixOps

/**
 * Use case of akka-management server and health check api
 *
 * https://doc.akka.io/docs/akka-management/current/akka-management.html
 */
trait AkkaManagementHttpApiSample {

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
       |    route-providers-read-only = false // enable the put/post/delete/ method
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
object AkkaManagementHttpApiSampleTest extends App with AkkaManagementHttpApiSample {

  val system1 = launch(config(25251, 8558))
  val system2 = launch(config(25252, 8559))
  val system3 = launch(disableHealthCheckConfig(25253, 8560))

  waitSystemUp(system1, system2, system3).printState
  Cluster(system1).manager ! Join(system3.address)
}


//noinspection EmptyParenMethodAccessedAsParameterless,DuplicatedCode
class AkkaManagementHttpApiRequestSpec extends AnyWordSpec with Matchers {

  import spray.json._
  import DefaultJsonProtocol._
  import sttp.client3.quick._

  val request = quickRequest
  val defRoute = "http://127.0.0.1:8558/mycluster"

  "Akka management api" should {

    "Health check" in {
      // check whether the node is ready
      request.get(uri"http://127.0.0.1:8558/mycluster/health/ready").send(backend).thenPrint.assert { rsp => rsp.body shouldBe "OK" }
      request.get(uri"http://127.0.0.1:8559/mycluster/health/ready").send(backend).thenPrint.assert { rsp => rsp.body shouldBe "OK" }
      request.get(uri"http://127.0.0.1:8560/mycluster/health/ready").send(backend).thenPrint.assert { rsp => rsp.code shouldBe NotFound }

      // check whether the node is alive
      request.get(uri"http://127.0.0.1:8558/mycluster/health/alive").send(backend).thenPrint.assert { rsp => rsp.body shouldBe "OK" }
      request.get(uri"http://127.0.0.1:8559/mycluster/health/alive").send(backend).thenPrint.assert { rsp => rsp.body shouldBe "OK" }
      request.get(uri"http://127.0.0.1:8560/mycluster/health/alive").send(backend).thenPrint.assert { rsp => rsp.code shouldBe NotFound }
    }

    "Cluster members operation" in {
      // get all cluster members
      request.get(uri"$defRoute/cluster/members/").send(backend).thenPrintJson

      // get the status of specified member
      request.get(uri"$defRoute/cluster/members/ClusterSystem@127.0.0.1:25251").send(backend).thenPrintJson

      // leave the specified node from cluster
      request.put(uri"$defRoute/cluster/members/ClusterSystem@127.0.0.1:25252")
        .body("operation" -> "Leave")
        .send(backend).thenPrint

      // join the specified node to cluster
      request.post(uri"$defRoute/cluster/members/")
        .body("address" -> "akka://ClusterSystem@127.0.0.1:25252")
        .send(backend).thenPrint
    }

    // returns cluster domain events as they occur, in JSON-encoded SSE format.
    "Get domain events" in {

    }
  }


  implicit class ResponseWrapped(reps: Response[String]) {
    def thenPrint: Response[String] = {
      println(
        s"""request-to: ${reps.request.method} ${reps.request.uri}
           |code: ${reps.code}
           |${reps.body}
           |--------------------------------------------------------""".stripMargin)
      reps
    }
    def thenPrintJson: Response[String] = {
      println(
        s"""request-to: ${reps.request.method} ${reps.request.uri}
           |code: ${reps.code}
           |${reps.body.parseJson.prettyPrint}
           |--------------------------------------------------------""".stripMargin)
      reps
    }
    def assert(func: Response[String] => _): Unit = {
      func(reps)
    }
  }
}


/**
 * akka-manager domain-events received api test
 */
object AkkaManagementHttpApiEventRequestTest extends App {
  implicit val system = ActorSystem(Behaviors.empty, "sse-client")
  implicit val ec = system.executionContext
  implicit val um = EventStreamUnmarshalling.fromEventsStream(ServerSentEventSettingsImpl(system.settings.config))

  Http().singleRequest(Get("http://127.0.0.1:8558/mycluster/cluster/domain-events"))
    .flatMap(Unmarshal(_).to[Source[ServerSentEvent, NotUsed]])
    .foreach(source => source.runForeach {
      case ServerSentEvent(data, _, _, _) => println(data)
    })
}
