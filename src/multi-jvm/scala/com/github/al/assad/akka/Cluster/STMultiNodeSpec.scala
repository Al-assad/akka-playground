package com.github.al.assad.akka.Cluster

import akka.remote.testkit.{MultiNodeSpec, MultiNodeSpecCallbacks}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.language.implicitConversions

/**
 * Scalatest scaffolding for multi-node testing
 * run all test: multi-jvm:test
 */
trait STMultiNodeSpec extends MultiNodeSpecCallbacks with AnyWordSpecLike with Matchers with BeforeAndAfterAll {
  self: MultiNodeSpec =>

  override def beforeAll() = multiNodeSpecBeforeAll()

  override def afterAll() = multiNodeSpecAfterAll()

  override implicit def convertToWordSpecStringWrapper(s: String): WordSpecStringWrapper =
    new WordSpecStringWrapper(s"$s (on node '${self.myself.name}', $getClass)")

}
