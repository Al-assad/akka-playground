package com.github.al.assad.akka.Persistence

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import com.typesafe.config.Config
import org.scalatest.BeforeAndAfterEach
import org.scalatest.wordspec.AnyWordSpecLike

/**
 * Akka persistence jdbc backend test kit.
 * This kit will automatically create jdbc schema before and after all tests.
 */
class AkkaPersistenceJdbcTestKit(config: Config,
                                 clearSchemaBeforeAll: Boolean = false,
                                 clearSchemaAfterAll: Boolean = false) extends ScalaTestWithActorTestKit(config)
  with AnyWordSpecLike
  with BeforeAndAfterEach {

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    if (clearSchemaBeforeAll) SchemaGenerator.dropSchema(config)
    SchemaGenerator.createSchema(config)
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    if (clearSchemaAfterAll) SchemaGenerator.dropSchema(config)
  }

}
