package com.github.al.assad.akka

import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.ActorRef

object TestImplicit {

  implicit class TestProbeWrapper[M](probe: TestProbe[M]) {

    def to[R](func: TestProbe[M] => R): R = func(probe)

    def from(func: ActorRef[M] => Unit): TestProbe[M] = {
      func(probe.ref)
      probe
    }
  }


}


