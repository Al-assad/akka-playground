package com.github.al.assad.akka

import akka.actor.testkit.typed.scaladsl.{LogCapturing, TestProbe}
import akka.actor.typed.{ActorRef, ActorSystem}
import org.scalatest.wordspec.AnyWordSpecLike

object TestImplicit {

  implicit class TestProbeWrapper[M](probe: TestProbe[M]) {
    def to[R](func: TestProbe[M] => R): R = func(probe)
    def from(func: ActorRef[M] => Unit): TestProbe[M] = {
      func(probe.ref)
      probe
    }
  }
}


trait STAkkaSpec extends AnyWordSpecLike with LogCapturing {

  /*  implicit class TestProbeWrapper[M](probe: TestProbe[M]) {
      def to[R](func: TestProbe[M] => R): R = func(probe)
      def from(func: ActorRef[M] => Unit): TestProbe[M] = {
        func(probe.ref)
        probe
      }
    }*/

  def probe[M](func: TestProbe[M] => Any)(implicit system: ActorSystem[Nothing]): TestProbe[M] = {
    val probe = TestProbe[M]()
    func(probe)
    probe
  }

  def probeRef[M](func: ActorRef[M] => Any)(implicit system: ActorSystem[Nothing]): TestProbe[M] = {
    val probe = TestProbe[M]()
    func(probe.ref)
    probe
  }


}

