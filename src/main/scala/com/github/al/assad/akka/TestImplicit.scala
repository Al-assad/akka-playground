package com.github.al.assad.akka

import akka.actor.testkit.typed.scaladsl.{LogCapturing, TestProbe}
import akka.actor.typed.{ActorRef, ActorSystem}
import com.typesafe.config.ConfigFactory
import org.scalatest.Suite
import org.scalatest.wordspec.AnyWordSpecLike

object TestImplicit {

  implicit class TestProbeWrapper[M](probe: TestProbe[M]) {
    def to[R](func: TestProbe[M] => R): R = func(probe)
    def from(func: ActorRef[M] => Unit): TestProbe[M] = {
      func(probe.ref)
      probe
    }
  }

  def testProbe[M](func: TestProbe[M] => Any)(implicit system: ActorSystem[Nothing]): TestProbe[M] = {
    val probe = TestProbe[M]()
    func(probe)
    probe
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

  val logListenerConfig = ConfigFactory.parseString("""akka.loggers = ["akka.testkit.TestEventListener"]""")

  def testProbe[M](func: TestProbe[M] => Any)(implicit system: ActorSystem[Nothing]): TestProbe[M] = {
    val probe = TestProbe[M]()
    func(probe)
    probe
  }

  def testProbeRef[M](func: ActorRef[M] => Any)(implicit system: ActorSystem[Nothing]): TestProbe[M] = {
    val probe = TestProbe[M]()
    func(probe.ref)
    probe
  }

}

class RunSpec(spec: Suite, testName: String) extends App {
    spec.execute(testName = testName)
}

// object ActorStyleSpec1 extends RunSpec(new ActorStyleSpec,"Counter should functional style")
