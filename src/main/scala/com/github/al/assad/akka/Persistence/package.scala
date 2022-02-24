package com.github.al.assad.akka

import com.typesafe.config.ConfigFactory

package object Persistence {

  val inmenBackendConf = ConfigFactory.load("persistence-inmen")

  val jdbcBackendConf = ConfigFactory.load("persistence-jdbc")

}
