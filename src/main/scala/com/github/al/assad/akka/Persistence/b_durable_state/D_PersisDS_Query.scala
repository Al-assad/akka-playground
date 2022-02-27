package com.github.al.assad.akka.Persistence.b_durable_state

import akka.NotUsed
import akka.persistence.query.scaladsl.DurableStateStoreQuery
import akka.persistence.query.{DurableStateChange, Offset, UpdatedDurableState}
import akka.persistence.state.DurableStateStoreRegistry
import akka.stream.scaladsl.Source
import com.github.al.assad.akka.Persistence.b_durable_state.PersistDSSample.Articles
import com.github.al.assad.akka.Persistence.b_durable_state.PersistDSSample.Articles.{Add, ArticleSummary, GetSummary}
import com.github.al.assad.akka.Persistence.{AkkaPersistenceJdbcTestKit, jdbcBackendJsonSerConf}
import com.github.al.assad.akka.TestImplicit.TestProbeWrapper

/**
 * Akka Persistence Duration State Query sample.
 */
//noinspection DuplicatedCode,ScalaFileName
class PersisDSQuerySpec extends AkkaPersistenceJdbcTestKit(jdbcBackendJsonSerConf, clearSchemaBeforeAll = false){

  "fill state data" should {
    "behave normally" in {
      val articles = spawn(Articles("alex-article"))
      (1 to 20).foreach(i => articles ! Add(i, s"title-$i", system.ignoreRef))
      createTestProbe[ArticleSummary]() to { probe =>
        articles ! GetSummary(probe.ref)
        val summary = probe.receiveMessage()
        println(summary)
      }
    }
  }

  def getQuery[Record](offset: Offset, tag: String) = {
    val pluginId = "jdbc-durable-state-store"
    val durableStateStoreQuery =
      DurableStateStoreRegistry(system).durableStateStoreFor[DurableStateStoreQuery[Articles.ArticleSummary]](pluginId)
    val source: Source[DurableStateChange[Articles.ArticleSummary], NotUsed] =
      durableStateStoreQuery.changes(tag, offset)
    source.map {
      case UpdatedDurableState(persistenceId, revision, value, offset, timestamp) => value
    }
  }

  "query jdbc durable state" should {
    "test1" in {
      getQuery[Articles.ArticleSummary](Offset.noOffset, "ds-article-alex-article").runForeach(println)
    }

  }
}
