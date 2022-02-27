package com.github.al.assad.akka.Persistence.a_event_sourcing

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, RetentionCriteria}
import com.github.al.assad.akka.Persistence.{CborSerializable, inmenBackendConf}
import com.github.al.assad.akka.TestImplicit.TestProbeWrapper
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration.DurationInt


/**
 * A Complete Akka Persistence Example.
 */
//noinspection DuplicatedCode
object PersistenceSample {

  object Articles {

    // cqrs command
    sealed trait Command extends CborSerializable
    final case class Add(id: Int, title: String, replyTo: ActorRef[AddResult]) extends Command // or use ActorRef[StatusReply]
    final case class Publish(id: Int) extends Command
    final case class Remove(id: Int) extends Command
    final case class Like(id: Int) extends Command
    final case object Clear extends Command
    final case class Get(id: Int, replyTo: ActorRef[Option[Article]]) extends Command
    final case class GetSummary(replyTo: ActorRef[ArticleSummary]) extends Command

    final case class AddResult(success: Boolean, reason: Option[String] = None) extends CborSerializable

    // cqrs event
    sealed trait Event extends CborSerializable
    final case class Added(id: Int, title: String) extends Event
    final case class Published(id: Int) extends Event
    final case class Removed(id: Int) extends Event
    final case class Liked(id: Int) extends Event
    final case object Cleared extends Event

    // state
    final case class State(items: Map[Int, Article]) extends CborSerializable {
      def hasItem(id: Int): Boolean = items.contains(id)
      def isPublish(id: Int): Boolean = items.get(id).exists(_.isPublish)
      def getItem(id: Int): Option[Article] = items.get(id)
      def addItem(id: Int, title: String): State = copy(items + (id -> Article(title)))
      def removeItem(id: Int): State = copy(items - id)
      def publishItem(id: Int): State = copy(items.updated(id, items(id).copy(isPublish = true)))
      def likeItem(id: Int): State = copy(items.updated(id, items(id).copy(likes = items(id).likes + 1)))
      def clearItems: State = copy(items = Map.empty)
      def toSummary: ArticleSummary = ArticleSummary(items.values.toSeq)
    }
    object State {
      def default: State = State(Map.empty)
    }

    final case class Article(title: String, isPublish: Boolean = false, likes: Int = 0) extends CborSerializable
    final case class ArticleSummary(articles: Seq[Article]) extends CborSerializable

    // command handler
    private def onCommand(state: State, command: Command): Effect[Event, State] = command match {
      case Add(id, title, replyTo) =>
        if (state.hasItem(id))
          Effect.none.thenReply(replyTo)(state =>
            AddResult(success = false, Some(s"Article already exists, title=${state.getItem(id).get.title}")))
        else
          Effect.persist(Added(id, title)).thenReply(replyTo)(_ => AddResult(success = true))

      case Publish(id) => if (state.hasItem(id)) Effect.persist(Published(id)) else Effect.none
      case Remove(id) => if (state.hasItem(id)) Effect.persist(Removed(id)) else Effect.none
      case Like(id) => if (state.isPublish(id)) Effect.persist(Liked(id)) else Effect.none
      case Clear => Effect.persist(Cleared)
      case Get(id, replyTo) => Effect.reply(replyTo)(state.getItem(id))
      case GetSummary(replyTo) => Effect.reply(replyTo)(state.toSummary)
    }

    // event handler
    private def onEvent(state: State, event: Event): State = event match {
      case Added(id, title) => state.addItem(id, title)
      case Published(id) => state.publishItem(id)
      case Removed(id) => state.removeItem(id)
      case Liked(id) => state.likeItem(id)
      case Cleared => state.clearItems
    }


    def apply(userId: String): Behavior[Command] = EventSourcedBehavior[Command, Event, State](
      persistenceId = PersistenceId.ofUniqueId(userId),
      emptyState = State.default,
      commandHandler = (state, command) => onCommand(state, command),
      eventHandler = (state, event) => onEvent(state, event))
      .withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = 100, keepNSnapshots = 2).withDeleteEventsOnSnapshot)
      .onPersistFailure(SupervisorStrategy.restartWithBackoff(200.millis, 5.seconds, 0.1))

  }
}

//noinspection DuplicatedCode
class PersistenceSampleSpec extends ScalaTestWithActorTestKit(inmenBackendConf) with AnyWordSpecLike {

  import PersistenceSample._
  import Articles._

  "Articles" should {

    "behave normally" in {
      val articles = spawn(Articles("assad"))
      createTestProbe[AddResult]() to { probe =>
        articles ! Add(1, "title1", probe.ref)
        probe.expectMessage(AddResult(success = true))
      }
      createTestProbe[Option[Article]]() to { probe =>
        articles ! Get(1, probe.ref)
        probe.expectMessage(Some(Article("title1")))
      }
      articles ! Publish(1)
      (1 to 3).foreach(_ => articles ! Like(1))
      createTestProbe[Option[Article]]() to { probe =>
        articles ! Get(1, probe.ref)
        probe.receiveMessage().get.likes shouldBe 3
      }
      articles ! Remove(1)
      createTestProbe[Option[Article]]() to { probe =>
        articles ! Get(1, probe.ref)
        probe.expectMessage(None)
      }

      (10 to 20).foreach(i => articles ! Add(i, s"title-$i", system.ignoreRef))
      createTestProbe[ArticleSummary]() to { probe =>
        articles ! GetSummary(probe.ref)
        val summary = probe.receiveMessage()
        println(summary)
        summary.articles.size shouldBe 11
      }
    }
  }

}
