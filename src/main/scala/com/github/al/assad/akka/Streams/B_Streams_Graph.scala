package com.github.al.assad.akka.Streams

import akka.NotUsed
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.stream.ClosedShape
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Keep, Merge, RunnableGraph, Sink, Source, Zip}
import com.github.al.assad.akka.TestImplicit.WrappedFuture
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * Akka Streams working with graph sample.
 *
 * https://doc.akka.io/docs/akka/current/stream/stream-graphs.html
 *
 * Operators:
 * - Fan-out(1 input, N output): Broadcast, Balance, Unzip
 * - Fan-in(N input, 1 output): Merge, Zip, Concat
 *
 */
class StreamsGraphSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {


  "Akka Streams Graph" should {

    "base-1: broadcast and merge" in {
      val graph = RunnableGraph.fromGraph(GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
        import GraphDSL.Implicits._

        val in = Source(1 to 10)
        val out = Sink.foreach(println)

        val broadcast = builder.add(Broadcast[Int](2))
        val merge = builder.add(Merge[Int](2))

        val f1, f2, f3 = Flow[Int].map(_ + 10)

        in ~> f1 ~> broadcast ~> f2 ~> merge ~> out
        broadcast ~> f3 ~> merge
        ClosedShape
      })
      graph.run()
    }


    "base-2: broadcast" in {
      val firstElementSink = Sink.head[Int]
      val lastElementSink = Sink.last[Int]
      val flow = Flow[Int].map(_ * 2)

      val graph = RunnableGraph.fromGraph(GraphDSL.createGraph(firstElementSink, lastElementSink)((_, _)) { implicit builder =>
        (firstElement, lastElement) =>
          import GraphDSL.Implicits._

          val broadcast = builder.add(Broadcast[Int](2))
          Source(1 to 10) ~> broadcast.in

          broadcast ~> flow ~> firstElement.in
          broadcast ~> flow ~> lastElement.in
          ClosedShape
      })

      val result: (Future[Int], Future[Int]) = graph.run()
      result._1.futureValue shouldBe 2
      result._2.futureValue shouldBe 20
    }


    "base-3: broadcast dynamic numbers" in {
      val sinks = Seq("a", "b", "c").map(prefix =>
        Flow[String]
          .filter(_.startsWith(prefix))
          .fold(0)((acc, _) => acc + 1)
          .map(e => s"$prefix => $e")
          .toMat(Sink.head[String])(Keep.right)
      )
      val graph: RunnableGraph[Seq[Future[String]]] = RunnableGraph.fromGraph(GraphDSL.create(sinks) { implicit builder =>
        sinkList =>
          import GraphDSL.Implicits._
          val broadcast = builder.add(Broadcast[String](sinkList.size))

          Source(List("ax", "bx", "cx", "ay", "dw")) ~> broadcast.in
          sinkList.foreach(sink => broadcast ~> sink)
          ClosedShape
      })
      val result = graph.run()
      Future.sequence(result).futureValue shouldBe List("a => 2", "b => 1", "c => 1")
    }


    "base-4: broadcast output to specify port" in {
      val source = Source(1 to 1000)
      val sink = Sink.head[(Int, Int)]

      val graph = RunnableGraph.fromGraph(GraphDSL.createGraph(sink) { implicit builder =>
        sink =>
          import GraphDSL.Implicits._
          val broadcast = builder.add(Broadcast[Int](2))
          val zip = builder.add(Zip[Int, Int]())
          val count = Flow[Int].fold(0)((acc, _) => acc + 1)

          source ~> broadcast
          broadcast.out(0) ~> Flow[Int].filter(_ % 2 == 0) ~> count ~> zip.in0
          broadcast.out(1) ~> Flow[Int].filter(_ % 2 == 1) ~> count ~> zip.in1

          zip.out ~> sink
          ClosedShape
      })
      val result = graph.run()
      result.futureValue shouldBe ((500, 500))
    }


  }

}
