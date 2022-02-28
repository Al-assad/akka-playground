package com.github.al.assad.akka.Streams

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.testkit.scaladsl.TestSink
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

/**
 * use case of akka-streams-testkit
 * https://doc.akka.io/docs/akka/current/stream/stream-testkit.html
 */
class AkkaStreamsTestKitSampleSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  "build-in test" should {

    "base-1" in {
      val sinkUnderTest = Flow[Int].map(_ * 2).toMat(Sink.fold(0)(_ + _))(Keep.right)
      val future = Source(1 to 4).runWith(sinkUnderTest)

      Await.result(future, 3.seconds) shouldBe 20
    }

    "base-2" in {
      val sourceUnderTest = Source.repeat(1).map(_ * 2)
      val future = sourceUnderTest.take(10).runWith(Sink.seq)

      Await.result(future, 3.seconds) shouldBe Seq.fill(10)(2)
    }

    "base-3" in {
      val flowUnderTest = Flow[Int].takeWhile(_ < 5)
      val future = Source(1 to 10).via(flowUnderTest).runWith(Sink.fold(Seq.empty[Int])(_ :+ _))

      val result = Await.result(future, 3.seconds) shouldBe (1 to 4)
    }
  }


  "testkit" should {
    "base-1" in {
      val source = Source(1 to 4).filter(_ % 2 == 0).map(_ * 2)
      val probe = source.runWith(TestSink[Int]())

      probe.request(2) // request element numbers is 2
        .expectNext(4, 8) // expect elements
        .expectComplete()
    }

  }

}
