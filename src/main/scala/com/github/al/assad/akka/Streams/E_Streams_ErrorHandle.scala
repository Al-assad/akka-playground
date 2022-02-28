package com.github.al.assad.akka.Streams

import akka.actor.testkit.typed.scaladsl.{LogCapturing, ScalaTestWithActorTestKit}
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.stream.{ActorAttributes, Supervision}
import akka.stream.scaladsl.{Sink, Source}
import org.scalatest.wordspec.AnyWordSpecLike

/**
 * Akka Streams Error Handling
 *
 * https://doc.akka.io/docs/akka/current/stream/stream-error.html
 */
//noinspection DuplicatedCode
class StreamsErrorHandleSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike with LogCapturing {

  "Akka Streams Error Handling" should {

    "base-1: Logging errors" in {
      Source(-5 to 5)
        .map(1 / _) //throwing ArithmeticException: / by zero
        .log("error logging")
        .runWith(Sink.ignore)
    }


    "base-2: Recover from errors" in {
      Source(1 to 5)
        .map(n => if (n != 4) n.toString else throw new RuntimeException(s"Boom! => $n"))
        .recover {
          case e: Exception => s"recover from error!: ${e.getMessage}"
        }
        .runForeach(println)
      /*
          1
          2
          3
          recover from error!: Boom! => 4
      */
    }


    "base-3: Recover with retries" in {
      val planB = Source(-5 to -1)
      Source(1 to 5)
        .map(n => if (n != 4) n.toString else throw new RuntimeException(s"Boom! => $n"))
        .recoverWithRetries(attempts = 2, {
          case e: Exception => planB
        })
        .runForeach(println)
      /* 1 2 3 -5 -4 -3 -2 -1 */
    }


    "base-4: Custom Supervision" in {
      val decider: Supervision.Decider = {
        case _: IllegalStateException => Supervision.Resume
        case _ => Supervision.Stop
      }

      Source(1 to 5)
        .map(n => if (n != 4) n.toString else throw new IllegalStateException(s"Boom! => $n"))
        .withAttributes(ActorAttributes.supervisionStrategy(decider))
        .runForeach(println)
      /* 1 2 3 5 */
    }


  }

}


object StreamsLoggingError extends App {
  implicit val system = ActorSystem(Behaviors.empty, "StreamsLoggingError")
  Source(-5 to 5)
    .map(1 / _) //throwing ArithmeticException: / by zero
    .log("error logging")
    .runWith(Sink.ignore)
}
