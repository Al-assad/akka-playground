package com.github.al.assad.akka.Persistence.a_event_sourcing

import akka.NotUsed
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal
import akka.persistence.query.{EventEnvelope, PersistenceQuery}
import akka.stream.scaladsl.Source
import com.github.al.assad.akka.Persistence.jdbcBackendJsonSerConf
import com.github.al.assad.akka.TestImplicit.testProbe
import com.github.nscala_time.time.Imports.DateTime
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.Date
import scala.concurrent.Await
import scala.concurrent.duration.{Duration, DurationInt}
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

  }

  // persistence query
  "query jdbc journal" should {

    // readJournal
    val readJournal: JdbcReadJournal =
      PersistenceQuery(system).readJournalFor[JdbcReadJournal](JdbcReadJournal.Identifier)

    // all events: It is a endless stream
    val willNotCompletedStreams: Source[EventEnvelope, NotUsed] =
      readJournal.eventsByPersistenceId("assad", 0, Long.MaxValue)

    // current events: It a enable stream
    val willCompletedStreams: Source[EventEnvelope, NotUsed] =
      readJournal.currentEventsByPersistenceId("assad", 0, Long.MaxValue)

    "query all events" in {
      val f = willNotCompletedStreams.runForeach(println)
      Await.result(f, Duration.Inf)
    }

    "query all currents events" in {
      val f = willCompletedStreams.runForeach(println)
      Await.result(f, Duration.Inf)
    }

    "query events with filter" in {
      val f = willCompletedStreams.filter(e => e.event.isInstanceOf[Added]).runForeach(println)
      Await.result(f, Duration.Inf)
    }

    "query events with mapper" in {
      val f = willCompletedStreams
        .filter(_.event.isInstanceOf[Liked])
        .filter(_.timestamp > DateTime.now.minusHours(5).getMillis)
        .map(e => e.event.asInstanceOf[Liked].id -> new DateTime(new Date(e.timestamp)).toString("yyyy-MM-dd HH:mm:ss.SSS"))
        .runForeach(println)
      Await.result(f, Duration.Inf)
    }
  }

}
