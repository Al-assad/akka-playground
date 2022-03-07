package com.github.al.assad.akka.Http.b_server_api

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.Future

class H_Directives extends AnyWordSpec with Matchers with ScalatestRouteTest {


  "Directives" should {

    "Automatic Tuple extraction" in {
      def ping(boom: Boolean) = Future(if (boom) "boom" else "pong")

      val route: Route = (path("ping") & parameter("boom".as[Boolean].optional)) { boom =>
        // directly use the success of future
        onSuccess(ping(boom.getOrElse(false))) {
          case "pong" => complete("OK")
          case "boom" => complete(500, "Boom!")
        }
      }

      Get("/ping") ~> route ~> check {
        status shouldBe 200
        responseAs[String] shouldBe "OK"
      }

      Get("/ping?boom=true") ~> route ~> check {
        status shouldBe 500
        responseAs[String] shouldBe "Boom!"
      }
    }

  }


}
