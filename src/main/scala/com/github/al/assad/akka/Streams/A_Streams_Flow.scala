package com.github.al.assad.akka.Streams

import akka.Done
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.stream.scaladsl.{Flow, Keep, RunnableGraph, Sink, Source}
import com.github.al.assad.akka.TestImplicit.WrappedFuture
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

/**
 * Akka Streams working with flow sample.
 *
 * https://doc.akka.io/docs/akka/current/stream/stream-flows-and-basics.html
 */
//noinspection DuplicatedCode
class StreamsFlowSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {


  // Akka Streams Flow sample
  "Akka Streams Flows" should {

    "base-1" in {
      val source = Source(1 to 10)
      source.runForeach(println) // equals: source.runWith(Sink.foreach(println))
      Source(1 to 10).filter(_ % 2 == 0).map(_ + 1).runForeach(println)
    }


    "base-2-1: RunnableGraph" in {
      val source = Source(1 to 10)
      val sink = Sink.fold[Int, Int](0)(_ + _)

      val runnable: RunnableGraph[Future[Int]] = source.toMat(sink)(Keep.right) // connect source to sink
      val sum: Future[Int] = runnable.run()

      println(Await.result(sum, 1 seconds)) // 55
    }

    "base-2-2: RunnableGraph" in {
      val source = Source(1 to 10)
      val sink = Sink.fold[Int, Int](0)(_ + _)

      val sum: Future[Int] = source.runWith(sink)
      println(sum.receive()) // 55
    }

    "base-2-3: RunnableGraph" in {
      val source = Source(1 to 10)
      val sink = Sink.foreach(println)
      val flow = Flow[Int].fold(0)(_ + _)

      val f: Future[Done] = source.via(flow).runWith(sink) // 55
      f.receive()
    }


    "base-3: Consume the same Sources" in {
      val source = Source(1 to 10)
      val sink = Sink.fold[Int, Int](0)(_ + _)
      var sum1 = source.runWith(sink)
      var sum2 = source.runWith(sink)
      println("sum1 == sum2 ? ", sum1 == sum2) // false
      println("sum1:", sum1.receive()) // 55
      println("sum2:", sum2.receive()) // 55


      val runnable = source.toMat(sink)(Keep.right)
      sum1 = runnable.run()
      sum2 = runnable.run()
      println("sum1 == sum2 ? ", sum1 == sum2) // false
      println("sum1:", sum1.receive()) // 55
      println("sum2:", sum1.receive()) // 55
    }


    "base-4: Define Source and Sink" in {
      val source1 = Source(List(1, 2, 3)) // from an Iterable
      val source2 = Source.future(Future.successful("Hello Streams!")) // from Future
      val source3 = Source.single("Hello Streams!") // from a single element
      val source4 = Source.empty // empty source

      val sink1: Sink[Int, Future[Int]] = Sink.fold(0)(_ + _)
      val sink2 = Sink.head // get the first elements
      val sink3 = Sink.ignore // discard all elements
      val sink4 = Sink.foreach[String](println) // print all elements
    }


    "base-5: Parallel Operator" in {
      // non-parallel flow
      // source, filter, fold, sink ops are executed in a actor
      Source(1 to 1000)
        .filter(_ % 2 == 0)
        .fold(0)((acc, _) => acc + 1)
        .runWith(Sink.ignore)

      // parallel flow
      // source, filter ops are executed in a actor, but fold, sink ops are executed in another actor
      Source(1 to 10000)
        .filter(_ % 2 == 0)
        .async
        .fold(0)((acc, _) => acc + 1)
        .runWith(Sink.ignore)
    }


    "base-6: Throttle with time" in {
      // A flow that internally throttles elements to 1/second
      val f = Source(1 to 100)
        .throttle(1, 1 seconds)
        .runForeach(println)
      f.receive()
    }


    "base-7: Combining materialized values" in {
      val source = Source(1 to 10)
      val flow = Flow[Int].fold(0)(_ + _)
      val sink = Sink.head[Int]

      // By default, the materialized value of the leftmost stage is preserved
      val r1: Future[Int] = source.via(flow).runWith(sink)
      println("r1", r1.receive())

      // Simple selection of materialized values by using Keep.right
      val r2 = source.viaMat(flow)(Keep.right).toMat(sink)(Keep.right).run()
      println("r2", r2.receive())

      val r3 = source.via(flow).toMat(sink)(Keep.right).run()
      println("r3", r3.receive())

      // Using runWith will always give the materialized values of the stages added
      val r4 = source.via(flow).runWith(sink)
      println("r4", r4.receive())

      val r5 = flow.runWith(source, sink)
      println("r5", r5._2.receive())
    }

  }

}


