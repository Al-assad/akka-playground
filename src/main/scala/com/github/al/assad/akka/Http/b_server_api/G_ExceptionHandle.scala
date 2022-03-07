package com.github.al.assad.akka.Http.b_server_api

import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.StatusCodes.InternalServerError
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ExceptionHandler, Route}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.github.al.assad.akka.Http.b_server_api.ExceptionHandleSample.myExceptionHandler
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * akka-http handle exception sample.
 *
 * https://doc.akka.io/docs/akka-http/current/routing-dsl/exception-handling.html
 */
//noinspection DuplicatedCode
object ExceptionHandleSample {

  def myExceptionHandler: ExceptionHandler = ExceptionHandler {
      case _: ArithmeticException =>
        extractUri { uri =>
          println(s"Request to $uri could not be handled normally")
          complete(HttpResponse(InternalServerError, entity = "Bad numbers, bad result!!!"))
        }
      case _ =>
        extractUri { uri =>
          println(s"Request to $uri could not be handled normally")
          complete(HttpResponse(InternalServerError, entity = "Bad result!!!"))
        }
    }

}


//noinspection DuplicatedCode
class ExceptionHandleSample extends AnyWordSpec with Matchers with ScalatestRouteTest {
  "akka http" should {

    "custom exception handler" in {
      val route = handleExceptions(myExceptionHandler) {
        path("divide") {
          complete((1/0).toString)
        }
      }
      Get("/divide") ~> route ~> check {
        status shouldBe InternalServerError
        responseAs[String] shouldBe "Bad numbers, bad result!!!"
      }
    }

    "custom exception handler using Route seal" in {
      implicit val exHandler = myExceptionHandler
      val route = Route.seal {
        path("divide") {
          complete((1/0).toString)
        }
      }
      Get("/divide") ~> route ~> check {
        status shouldBe InternalServerError
        responseAs[String] shouldBe "Bad numbers, bad result!!!"
      }

    }
  }

}



