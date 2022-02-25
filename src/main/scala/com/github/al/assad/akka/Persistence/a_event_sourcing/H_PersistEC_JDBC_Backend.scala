package com.github.al.assad.akka.Persistence.a_event_sourcing

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import com.github.al.assad.akka.Persistence.jdbcBackendJsonSerConf
import com.github.al.assad.akka.TestImplicit.testProbe
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

/**
 * Akka Persistence JDBC backend example.
 *
 * https://doc.akka.io/docs/akka-persistence-jdbc/current/
 *
 * This example is the jdbc-backend version of [[PersistenceSampleSpec]] using PostgresSql as backend.
 * PostgresSql Schema: https://github.com/akka/akka-persistence-jdbc/blob/master/core/src/main/resources/schema/postgres/postgres-create-schema.sql
 */
//noinspection DuplicatedCode
class PersistenceJDBCBackendSpec
  extends ScalaTestWithActorTestKit(jdbcBackendJsonSerConf) with AnyWordSpecLike {

  import PersistenceSample._
  import Articles._

  "Article" should {

    "add articles" in {
      val articles = spawn(Articles("assad"))
      articles ! Add(1, "Akka Persistence", system.ignoreRef)
      articles ! Add(2, "Scala in Action", system.ignoreRef)
      articles ! Add(3, "Grand Grimoire", system.ignoreRef)

      val probe = createTestProbe[ArticleSummary]()
      articles ! GetSummary(probe.ref)
      println(probe.receiveMessage())
    }

    "like/publish articles" in {
      val articles = spawn(Articles("assad"))
      articles ! Publish(2)
      articles ! Publish(3)

      articles ! Like(1)
      articles ! Like(2)
      (1 to 20).foreach(_ => articles ! Like(3))
      testProbe[ArticleSummary] { probe =>
        articles ! GetSummary(probe.ref)
        println(probe.receiveMessage())
      }

      testProbe[Option[Article]] { probe =>
        articles ! Get(1, probe.ref)
        probe.receiveMessage().get.likes shouldBe 0
      }
    }

    "remove articles" in {
      val articles = spawn(Articles("assad"))
      articles ! Remove(1)
      articles ! Remove(2)
      articles ! Remove(3)
      testProbe[ArticleSummary] { probe =>
        articles ! GetSummary(probe.ref)
        println(probe.receiveMessage())
      }
    }

    "read all article" in {
      val articles = spawn(Articles("assad"))
      val probe = createTestProbe[ArticleSummary]()
      articles ! GetSummary(probe.ref)
      println(probe.receiveMessage())
    }

    "testing snapshot" in {
      val articles = spawn(Articles("assad"))
      articles ! Add(1, "Akka Persistence", system.ignoreRef)
      articles ! Publish(1)
      (1 to 500).foreach(_ => articles ! Like(1))
      testProbe[Option[Article]] { probe =>
        articles ! Get(2, probe.ref)
        println(probe.receiveMessage(10.seconds))
      }
    }

    "testing persistence query" in {

    }

  }

}
