package com.github.al.assad.akka.Http.b_server_api

import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.StatusCodes.{BadRequest, Forbidden, InternalServerError, MethodNotAllowed, NotFound}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{AuthorizationFailedRejection, MethodRejection, MissingCookieRejection, RejectionHandler, Route, ValidationRejection}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.github.al.assad.akka.Http.b_server_api.RejectionsSample.myRejectionHandler
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * akka-http custom rejection behaviors.
 *
 * https://doc.akka.io/docs/akka-http/current/routing-dsl/rejections.html
 */
object RejectionsSample {

  def myRejectionHandler = RejectionHandler.newBuilder()
    .handle {
      case MissingCookieRejection(cookieName) =>
        complete(HttpResponse(BadRequest, entity = "No cookies, no service!!!"))
    }
    .handle {
      case AuthorizationFailedRejection =>
        complete(Forbidden, "You're out of your depth!")
    }
    .handle {
      case ValidationRejection(msg, _) =>
        complete(InternalServerError, "That wasn't valid! " + msg)
    }
    .handleAll[MethodRejection] { methodRejections =>
      val names = methodRejections.map(_.supported.name)
      complete(MethodNotAllowed, s"Can't do that! Supported: ${names mkString " or "}!")
    }
    .handleNotFound {
      complete((NotFound, "Not here!"))
    }
    .result()
}

class RejectionsSample extends AnyWordSpec with Matchers with ScalatestRouteTest {

  "akka http" should {

    "custom rejection behavior" in {
      val route: Route = handleRejections(myRejectionHandler) {
        (path("hello") & get) {
          complete("Hello!")
        }
      }

      Get("/nobody") ~> route ~> check {
        status shouldBe NotFound
        responseAs[String] shouldBe "Not here!"
      }

    }
  }

}


