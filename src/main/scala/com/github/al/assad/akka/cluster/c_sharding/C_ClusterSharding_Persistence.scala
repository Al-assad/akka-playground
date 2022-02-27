package com.github.al.assad.akka.Cluster.c_sharding

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityTypeKey}
import akka.util.Timeout
import com.github.al.assad.akka.Cluster.a_base.waitSystemUp
import com.github.al.assad.akka.Cluster.c_sharding.ClusterShardingPersistenceTest.UserArticlesService
import com.github.al.assad.akka.Persistence.a_event_sourcing.PersistenceSample.Articles
import com.github.al.assad.akka.Persistence.a_event_sourcing.PersistenceSample.Articles.ArticleSummary
import com.github.al.assad.akka.sleep
import com.typesafe.config.ConfigFactory

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

/**
 * Akka cluster sharding persistence sample.
 *
 * https://doc.akka.io/docs/akka/current/typed/cluster-sharding.html#persistence-example
 */
object ClusterShardingPersistenceTest {

  /**
   * The sharding persistence sample for [[com.github.al.assad.akka.Persistence.a_event_sourcing.PersistenceSample.Articles]]
   * Of course, the example can also be rewritten as Actor pattern.
   */
  class UserArticlesService(system: ActorSystem[_]) {

    private implicit val askTimeout: Timeout = 5.seconds

    val userTypeKey = EntityTypeKey[Articles.Command]("user-articles-service")
    private val sharding = ClusterSharding(system)
    private val userShardRegion: ActorRef[ShardingEnvelope[Articles.Command]] = sharding.init(
      Entity(userTypeKey) { entityContext =>
        Articles(entityContext.entityId)
      }
    )

    def addArticle(userId: String, articleId: Int, title: String): Unit = {
      userShardRegion ! ShardingEnvelope(entityId = userId, Articles.Add(articleId, title, system.ignoreRef))
    }

    def publishArticle(userId: String, articleId: Int): Unit = {
      userShardRegion ! ShardingEnvelope(entityId = userId, Articles.Publish(articleId))
    }

    def likeArticle(userId: String, articleId: Int): Unit = {
      userShardRegion ! ShardingEnvelope(entityId = userId, Articles.Like(articleId))
    }

    def clearArticle(userId: String): Unit = {
      userShardRegion ! ShardingEnvelope(entityId = userId, Articles.Clear)
    }

    def getUserAllArticles(userId: String): Future[ArticleSummary] = {
      val entityRef = sharding.entityRefFor(userTypeKey, userId)
      entityRef ? Articles.GetSummary
    }
  }
}



trait ClusterShardingPersistenceTest {

  def launch(port: Int): (ActorSystem[Nothing], UserArticlesService) = {
    val config = ConfigFactory
      .parseString(s"""akka.remote.artery.canonical.port=$port""".stripMargin)
      .withFallback(ConfigFactory.load("cluster-persistence-jdbc"))

    val system = ActorSystem[Nothing](Behaviors.empty, "ClusterSystem", config)
    val userArticlesService = new UserArticlesService(system)
    system -> userArticlesService
  }

}

/**
 *  Testing add/publish/like articles from different nodes.
 */
object ClusterShardingPersistenceTest1 extends App with ClusterShardingPersistenceTest {
  val (system1, service1) = launch(25251)
  val (system2, service2) = launch(25252)
  val (system3, service3) = launch(25253)
  waitSystemUp(system1, system2, system3).printState

  // clear articles
  service1.clearArticle("Adam")
  service1.clearArticle("Agatha")
  service1.clearArticle("Maria")

  // add articles
  service1.addArticle("Adam", 1, "The Initiated Interpretation Of Ceremonial Magic")
  service1.addArticle("Adam", 2, "Shemhamphorash")
  service2.addArticle("Agatha", 1, "Classified List of the 72 Chief Spirits")
  service3.addArticle("Maria", 1, "The Conjuration To Call Forth Any Of The Aforesaid Spirits")
  service3.addArticle("Maria", 2, "The Welcome Unto The Spirit")

  service1.getUserAllArticles("Adam") onComplete {
    case Success(summary) => println(summary)
    case Failure(ex) => ex.printStackTrace()
  }

  // publish and like articles
  service1.publishArticle("Adam", 2)
  service2.publishArticle("Agatha", 1)
  sleep(1.seconds)
  (1 to 20).foreach { _ =>
    service1.likeArticle("Adam", 2)
    service2.likeArticle("Agatha", 1)
  }

  service1.getUserAllArticles("Adam") onComplete {
    case Success(summary) => println(summary)
    case Failure(ex) => ex.printStackTrace()
  }
  service2.getUserAllArticles("Agatha") onComplete {
    case Success(summary) => println(summary)
    case Failure(ex) => ex.printStackTrace()
  }
}




