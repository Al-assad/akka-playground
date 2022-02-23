package com.github.al.assad.akka.Cluster.b_ddata

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import com.github.al.assad.akka.STAkkaSpec
import com.github.al.assad.akka.Cluster.b_ddata.DDataDurableStorageSpec.conf
import com.typesafe.config.ConfigFactory

/**
 * https://doc.akka.io/docs/akka/current/typed/distributed-data.html#durable-storage
 */

object DDataDurableStorageSpec {

  val conf = ConfigFactory.parseString(
    """
       akka.actor.provider = cluster
       akka.remote.classic.netty.tcp.port = 0
       akka.remote.artery.canonical.port = 0
       akka.remote.artery.canonical.hostname = 127.0.0.1

       akka.cluster.distributed-data.durable.keys = ["*"]
       akka.cluster.distributed-data.durable.lmdb {
         dir = lmdb-storage/DDataDurableStorageSpec
         write-behind-interval = 200ms
         map-size = 10 MiB
       }
    """)
}

class DDataDurableStorageSpec extends ScalaTestWithActorTestKit(conf) with STAkkaSpec {

  import YaCounterCache._

  "DDataDurableStorage" should {

    "test1" in {
      val cache = spawn(YaCounterCache("counter-cache-1"))
      cache ! Increment(0)

      val origin = testProbeRef[Int](cache ! GetValue(_)).receiveMessage()
      println(s"origin: $origin")

      cache ! Increment(20)
      cache ! Increment(30)

      testProbe[Int] { probe =>
        cache ! GetValue(probe.ref)
        probe.expectMessage(origin + 50)
      }
    }
  }
}
