package com.github.al.assad.akka.Streams

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.stream.SubstreamCancelStrategy
import akka.stream.scaladsl.{Sink, Source}
import org.scalatest.wordspec.AnyWordSpecLike

/**
 * Akka Streams - sub streams sample.
 *
 * https://doc.akka.io/docs/akka/current/stream/stream-substream.html
 */
class StreamsSubStreamsSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  "SubStreams: Nesting operators" should {

    "base-1: group by" in {
      val source = Source(1 to 10)
        .groupBy(maxSubstreams = 3, _ % 3)
        .map(_.toString)
        .reduce(_ + " " + _)

      source.to(Sink.foreach(println)).run()
      /*
        2 5 8
        3 6 9
        1 4 7 10
       */
    }

    "base-2: group by and merge substreams" in {
      val source = Source(1 to 10)
        .groupBy(maxSubstreams = 3, _ % 3)
        .reduce(_ + _)
        .mergeSubstreamsWithParallelism(2)

      source.runForeach(println)

      // concatSubstreams is equivalent to mergeSubstreamsWithParallelism(1)
      val source2 = Source(1 to 10)
        .groupBy(maxSubstreams = 3, _ % 3)
        .reduce(_ + _)
        .concatSubstreams

      source2.runForeach(println)

    }

    "base-3: splitWhen" in {
      val source = Source(1 to 10).splitWhen(SubstreamCancelStrategy.drain)(_ == 3)
        .map(_.toString)
        .reduce(_ + " " + _)
      source.to(Sink.foreach(println)).run()
      /*
        1 2
        3 4 5 6 7 8 9 10
       */
    }

    "base-4: splitAfter" in {
      val source = Source(1 to 10).splitAfter(SubstreamCancelStrategy.drain)(_ == 3)
        .map(_.toString)
        .reduce(_ + " " + _)
      source.to(Sink.foreach(println)).run()
      /*
        1 2 3
        4 5 6 7 8 9 10
       */
    }
  }


  "SubStreams: Flattening operators" should {

    "base-1: flatMapConcat" in {
      Source(1 to 2).flatMapConcat(i => Source(List.fill(3)(i))).runForeach(println)
      // 1 1 1 2 2 2
    }

    "base-2: flatMapMerge" in {
      // flatMapMerge is similar to flatMapConcat, but it doesn’t wait for one Source to be fully consumed.
      // Instead, up to breadth number of streams emit elements at any given time.
      Source(1 to 2).flatMapMerge(2, i => Source(List.fill(3)(i))).runForeach(println)
      // 2 1 2 1 2 1
    }
  }

}
