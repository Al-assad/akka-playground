package com.github.al.assad.akka.cluster

import akka.remote.testkit.MultiNodeConfig
import com.typesafe.config.ConfigFactory

/**
 * Multi-node configuration for cluster tests.
 */
object MultiNodeSpecConfig extends MultiNodeConfig {

  val node1 = role("node-1")
  val node2 = role("node-2")
  val node3 = role("node-3")

  commonConfig(ConfigFactory.parseString(
    """
    akka.loglevel = INFO
    akka.actor.provider = "cluster"
    akka.log-dead-letters-during-shutdown = off
    """))
}
