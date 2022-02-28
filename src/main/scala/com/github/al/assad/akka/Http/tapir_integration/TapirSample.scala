package com.github.al.assad.akka.Http.tapir_integration


import com.github.al.assad.akka.Http.tapir_integration.TapirSample.{booksListingRequest, booksListingRoute, docs}


object TapirSample {

  type Limit = Int
  type AuthToken = String
  case class BooksFromYear(genre: String, year: Int)
  case class Book(title: String)


  // Define an endpoint

  import io.circe.generic.auto._
  import sttp.tapir._
  import sttp.tapir.generic.auto._
  import sttp.tapir.json.circe._

  val booksListing: PublicEndpoint[(BooksFromYear, Limit, AuthToken), String, List[Book], Any] = endpoint
    .get
    .in(("books" / path[String]("genre") / path[Int]("year")).mapTo[BooksFromYear])
    .in(query[Limit]("limit").description("Maximum number of books to retrieve"))
    .in(header[AuthToken]("X-Auth-Token"))
    .errorOut(stringBody)
    .out(jsonBody[List[Book]])


  // Generate OpenAPI documentation

  import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter

  val docs = OpenAPIDocsInterpreter().toOpenAPI(booksListing, "My Bookshop", "1.0")


  // Convert to akka-http Route

  import akka.http.scaladsl.server.Route
  import sttp.tapir.server.akkahttp.AkkaHttpServerInterpreter

  import scala.concurrent.Future

  def bookListingLogic(bfy: BooksFromYear, limit: Limit, at: AuthToken): Future[Either[String, List[Book]]] = {
    Future.successful(Right(List(Book("The Sorrows of Young Werther"))))
  }

  val booksListingRoute: Route = AkkaHttpServerInterpreter().toRoute(booksListing.serverLogic((bookListingLogic _).tupled))


  // Convert to sttp Request

  import sttp.client3._
  import sttp.tapir.client.sttp.SttpClientInterpreter

  val booksListingRequest: Request[DecodeResult[Either[String, List[Book]]], Any] =
    SttpClientInterpreter()
      .toRequest(booksListing, Some(uri"http://localhost:8080"))
      .apply((BooksFromYear("SF", 2016), 20, "xyz-abc-123"))

}


object TapirAkkaServer {

  import akka.actor.typed.ActorSystem
  import akka.actor.typed.scaladsl.Behaviors
  import akka.http.scaladsl.Http

  implicit val system = ActorSystem(Behaviors.empty, "tapir-route-server")
  implicit val executionContext = system.executionContext

  def main(args: Array[String]): Unit = {
    Http().newServerAt("127.0.0.1", 8080).bind(booksListingRoute)
  }
}

object TapirOpenApiTest {

  import sttp.tapir.openapi.circe.yaml.RichOpenAPI

  def main(args: Array[String]): Unit = {
    println(docs.toYaml)
  }
}


object TapirClientCall {

  import sttp.client3.HttpURLConnectionBackend

  def main(args: Array[String]): Unit = {
    val backend = HttpURLConnectionBackend()
    val response = booksListingRequest.send(backend)
    println(response.body)
  }
}
