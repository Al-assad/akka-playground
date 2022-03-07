package com.github.al.assad.akka.Http.b_server_api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.server.Directives.{authenticateBasic, authorize, complete, path}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.Credentials
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * akka-http authorization sample.
 *
 * https://doc.akka.io/docs/akka-http/current/routing-dsl/directives/security-directives/authorize.html
 */

//noinspection DuplicatedCode
object AuthorizationSample {

  case class User(name: String)

  def myUserPassAuthenticator(credentials: Credentials): Option[User] =
    credentials match {
      case p@Credentials.Provided(id) if p.verify("p4ssw0rd") => Some(User(id))
      case _ => None
    }

  // check if user is authorized to perform admin actions:
  val admins = Set("assad")
  def hasAdminPermissions(user: User): Boolean = admins.contains(user.name)

  val route: Route = Route.seal {
    authenticateBasic(realm = "secure site", myUserPassAuthenticator) { user =>
      path("admin-lair") {
        authorize(hasAdminPermissions(user)) {
          complete(s"Welcome to the admin lair, ${user.name}!")
        }
      }
    }
  }

}


class AuthorizationSample extends AnyWordSpec with Matchers with ScalatestRouteTest {

  import AuthorizationSample._

  "akka http authorization" should {

    "test1" in {
      val cred = BasicHttpCredentials("John", "p4ssw0rd")
      Get("/admin-lair") ~> addCredentials(cred) ~> route ~> check {
        status shouldEqual StatusCodes.Forbidden
      }
    }

    "test2" in {
      val cred = BasicHttpCredentials("assad", "p4ssw0rd")
      Get("/admin-lair") ~> addCredentials(cred) ~> route ~> check {
        status shouldEqual StatusCodes.OK
      }
    }

  }

}
