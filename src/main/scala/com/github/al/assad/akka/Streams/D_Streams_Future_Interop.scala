package com.github.al.assad.akka.Streams

import akka.Done
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.stream.scaladsl.{Sink, Source}
import com.github.al.assad.akka.sleep
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

/**
 * Akka Streams Future and Actor interop sample.
 *
 * https://doc.akka.io/docs/akka/current/stream/futures-interop.html
 */
class StreamsFutureInterop extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  "Akka Streams Future Interop" should {

    "base-1" in {
      def sendAlertMsg(msg: String): Future[Done] = Future {
        sleep(1.seconds)
        println(s"sent alert message: $msg")
        Done
      }

      val task = Source(Seq("ac", "bc", "ae", "af", "ec", "fc", "gc", "hc"))
        .filter(_.startsWith("a"))
        .mapAsync(4)(msg => sendAlertMsg(msg))
        .collect({ case Done => "send message done" })
        .to(Sink.ignore)
      task.run()
      sleep(5.seconds)
    }
  }

}
