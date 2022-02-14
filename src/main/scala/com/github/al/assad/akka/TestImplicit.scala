package com.github.al.assad.akka

import akka.actor.testkit.typed.scaladsl.TestProbe

object TestImplicit {

  implicit class TestProbeWrapper[M](probe: TestProbe[M]) {
    def andThen[R](func: TestProbe[M] => R): R = func(probe)
  }

}
