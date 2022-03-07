package com.github.al.assad.akka.Http.b_server_api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.{BasicHttpCredentials, HttpChallenge, `WWW-Authenticate`}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.Credentials
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


/**
 * akka http Basic Authentication Sample
 *
 * https://doc.akka.io/docs/akka-http/current/routing-dsl/directives/security-directives/authenticateBasic.html
 */
object AuthenticationBASample {

  // authenticate sync
  def myUserPassAuthenticator(credentials: Credentials): Option[String] =
    credentials match {
      case p@Credentials.Provided(id) if p.verify("p4ssw0rd") => Some(id)
      case _ => None
    }

  val route: Route = Route.seal {
    path("login") {
      authenticateBasic(realm = "secure site", myUserPassAuthenticator) { user =>
        complete(s"The user $user is logged in")
      }
    }
  }

  // authenticate async
  def myUserPassAuthenticatorAsync(credentials: Credentials): Future[Option[String]] =
    credentials match {
      case p@Credentials.Provided(id) => Future {
        if (p.verify("p4ssw0rd")) Some(id)
        else None
      }
      case _ => Future.successful(None)
    }

  val route2 =
    Route.seal {
      path("secured") {
        authenticateBasicAsync(realm = "secure site", myUserPassAuthenticatorAsync) { userName =>
          complete(s"The user is '$userName'")
        }
      }
    }

}

class AuthenticationBASample extends AnyWordSpec with Matchers with ScalatestRouteTest {

  import AuthenticationBASample._

  "akka http BA authentication" should {

    "test1" in {
      Get("/login") ~> route ~> check {
        status shouldBe StatusCodes.Unauthorized
        responseAs[String] shouldBe "The resource requires authentication, which was not supplied with the request"
        header[`WWW-Authenticate`].get.challenges.head shouldBe HttpChallenge("Basic", Some("secure site"), Map("realm" -> "secure site"))
      }
    }

    "test2" in {
      val validCredentials = BasicHttpCredentials("John", "p4ssw0rd")
      Get("/login") ~> addCredentials(validCredentials) ~>
      route ~> check {
        responseAs[String] shouldEqual "The user John is logged in"
      }
    }

    "test3" in {
      val invalidCredentials = BasicHttpCredentials("Peter", "pan")
      Get("/login") ~> addCredentials(invalidCredentials) ~>
      route ~> check {
        status shouldEqual StatusCodes.Unauthorized
        responseAs[String] shouldEqual "The supplied authentication is invalid"
        header[`WWW-Authenticate`].get.challenges.head shouldEqual HttpChallenge("Basic", Some("secure site"), Map("charset" -> "UTF-8"))
      }
    }

  }
}



