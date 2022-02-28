package com.github.al.assad.akka.Streams

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.stream.{Attributes, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Sink, Source}
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration.DurationInt

/**
 * Akka Streams Buffer sample.
 *
 * https://doc.akka.io/docs/akka/current/stream/stream-rate.html
 *
 * The default buffer configuration:
 * akka.stream.materializer.max-input-buffer-size = 16
 */
class StreamsBufferSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  "Akka Streams Buffer" should {

    "base-1: Override the default buffer size configuration" in {
      // the buffer size of this map is 1
      val section = Flow[Int].map(_ * 2).async.addAttributes(Attributes.inputBuffer(initial = 1, max = 1))

      // the buffer size of this map is the default
      val flow = section.via(Flow[Int].map(_ / 2)).async
      val runnableGraph = Source(1 to 10).via(flow).to(Sink.foreach(elem => println(elem)))

      // override the default buffer size
      val withOverriddenDefaults = runnableGraph.withAttributes(Attributes.inputBuffer(initial = 64, max = 64))
    }


    "base-2: Buffer Strategy" in {
      val jobs = Source(1 to 10).throttle(10, 1.seconds).async

      // drops the oldest element from the buffer to make space for the new element.
      jobs.buffer(100, OverflowStrategy.dropHead)

      // drops the youngest element from the buffer to make space for the new element.
      jobs.buffer(100, OverflowStrategy.dropTail)

      // drops the new element.

      jobs.buffer(100, OverflowStrategy.dropNew)
      // completes the stream with failure.
      jobs.buffer(100, OverflowStrategy.fail)
    }

  }

}

