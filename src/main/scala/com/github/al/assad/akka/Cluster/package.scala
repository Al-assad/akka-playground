package com.github.al.assad.akka

import com.typesafe.config.ConfigFactory

package object Cluster {

  val singleClusterConf = ConfigFactory.parseString(
    """
       akka.actor.provider = cluster
       akka.remote.classic.netty.tcp.port = 0
       akka.remote.artery.canonical.port = 0
       akka.remote.artery.canonical.hostname = 127.0.0.1
    """)


}
