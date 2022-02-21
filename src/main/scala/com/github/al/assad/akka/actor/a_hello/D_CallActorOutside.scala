package com.github.al.assad.akka.actor.a_hello

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.util.Timeout
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success}

/**
 * visit Actor from outside
 */

// Hello Actor
object HelloActor {

  case class Greet(msg: String, replyTo: ActorRef[Greeted])
  case class Greeted(msg: String)

  def apply(): Behavior[Greet] = Behaviors.receiveMessage { message =>
    message.replyTo ! Greeted(s"Hello, ${message.msg}!")
    Behaviors.same
  }
}

// call Actor from outside
class HelloTest extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  "call future outside from HelloActor" should {

    // asking someone requires a timeout if the timeout hits without response
    // the ask is failed with a TimeoutException
    implicit val timeout: Timeout = 2.seconds
    // implicit ActorSystem in scope
    implicit val system: ActorSystem[_] = testKit.system
    // the response callback will be executed on this execution context
    implicit val ec: ExecutionContextExecutor = system.executionContext

    val helloActor: ActorRef[HelloActor.Greet] = spawn(HelloActor())

    // call HelloActor.Greet and receive the response of it as Future
    val result: Future[HelloActor.Greeted] = helloActor.ask(ref => HelloActor.Greet("Al-assad", ref))
    result.onComplete {
      case Success(HelloActor.Greeted(msg)) => println(s"received from HelloActor: ${msg}")
      case Failure(e) => println(s"Boo! ${e.getMessage}")
    }
  }

}
