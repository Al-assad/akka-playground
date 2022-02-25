package com.github.al.assad.akka

import com.typesafe.config.ConfigFactory

package object Persistence {

  val inmenBackendConf = ConfigFactory.load("persistence-inmen")

  val jdbcBackendConf = ConfigFactory.load("persistence-jdbc")

  // use jackson-json as serializer
  val jdbcBackendJsonSerConf = ConfigFactory.parseString(
    """akka {
      |  actor {
      |    serialization-bindings {
      |      "com.github.al.assad.akka.Persistence.CborSerializable" = jackson-json
      |    }
      |  }
      |}""".stripMargin)
    .withFallback(jdbcBackendConf)

  // use jackson-cbor as serializer
  val jdbcBackendCborSerConf = ConfigFactory.parseString(
    """akka {
      |  actor {
      |    serialization-bindings {
      |      "com.github.al.assad.akka.Persistence.CborSerializable" = jackson-cbor
      |    }
      |  }
      |}""".stripMargin)
    .withFallback(jdbcBackendConf)

}
