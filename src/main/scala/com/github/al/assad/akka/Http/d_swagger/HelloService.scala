package com.github.al.assad.akka.Http.d_swagger

import akka.http.scaladsl.server.{Directives, Route}
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.{Operation, Parameter}
import io.swagger.v3.oas.annotations.media.{Content, Schema}
import io.swagger.v3.oas.annotations.responses.ApiResponse
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.{GET, Path, Produces}

@Path("/hello")
class HelloService extends Directives with DefaultJsonFormats {

  val route: Route = getHello ~ getHelloSegment

  @GET
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(summary = "Return Hello greeting (anonymous)", description = "Return Hello greeting for anonymous request",
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Hello response",
        content = Array(new Content(schema = new Schema(implementation = classOf[String])))),
      new ApiResponse(responseCode = "500", description = "Internal server error"))
  )
  def getHello: Route =
    path("hello") {
      get {
        complete("hi")
      }
    }

  @GET
  @Path("{name}") //the token inside the curly brackets should match the parameter name in the @Operation annotation
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(summary = "Return Hello greeting", description = "Return Hello greeting for named user",
    parameters = Array(new Parameter(name = "name", in = ParameterIn.PATH, description = "user name")),
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Hello response",
        content = Array(new Content(schema = new Schema(implementation = classOf[String])))),
      new ApiResponse(responseCode = "500", description = "Internal server error"))
  )
  def getHelloSegment: Route =
    path("hello" / Segment) { name =>
      get {
        complete(s"hi, $name")
      }
    }
}
