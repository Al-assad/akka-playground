package com.github.al.assad.akka.Persistence.a_event_sourcing

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.Behavior
import akka.persistence.typed.scaladsl.{EventSourcedBehavior, RetentionCriteria}
import akka.persistence.typed._
import com.github.al.assad.akka.Persistence.a_event_sourcing.PersistenceSnapshot.PersistentActorWithSnapshot
import com.github.al.assad.akka.Persistence.inmenBackendConf
import com.github.al.assad.akka.sleep
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration.DurationInt

/**
 * Akka Persistence Snapshot Example (Event Sourcing).
 *
 * https://doc.akka.io/docs/akka/current/typed/persistence-snapshot.html
 *
 * Persistent actors can save snapshots of internal state every N events
 * or when a given predicate of the state is fulfilled, it always been used
 * to reduce recovery times drastically.
 */
//noinspection DuplicatedCode
object PersistenceSnapshot {

  import PersistenceUseCase.BasePersistentActor

  object PersistentActorWithSnapshot extends BasePersistentActor {
    def apply(id: String): Behavior[Command] =
      EventSourcedBehavior[Command, Event, State](
        persistenceId = PersistenceId.ofUniqueId(id),
        emptyState = State(Seq.empty),
        commandHandler = commandHandler,
        eventHandler = eventHandler
      )
        // enable snapshot
        .withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = 100, keepNSnapshots = 2).withDeleteEventsOnSnapshot)
        // receive signal of snapshot
        .receiveSignal {
          // snapshot failure sign
          case (_, SnapshotFailed(metadata, cause)) =>
            println(s"snapshot failed, ${cause.getMessage}")
            throw cause
          case (_, DeleteSnapshotsFailed(target, cause)) =>
            println(s"delete snapshot failed, ${cause.getMessage}")
            throw cause
          case (_, DeleteEventsFailed(toSequenceNr, cause)) =>
            println(s"delete event failed, ${cause.getMessage}")
            throw cause
          // snapshot completed sign
          case (_, SnapshotCompleted(metadata)) =>
            println(s"snapshot completed, metadata = $metadata")
          case (_, DeleteSnapshotsCompleted(metadata)) =>
            println(s"delete snapshot completed, metadata = $metadata")
          case (_, DeleteEventsCompleted(metadata)) =>
            println(s"delete event completed, metadata = $metadata")
        }
  }

}

class PersistenceSnapshotSpec extends ScalaTestWithActorTestKit(inmenBackendConf) with AnyWordSpecLike {

  import PersistentActorWithSnapshot._

  "PersistentActorWithSnapshot" should {
    "behave normally" in {
      val actor = spawn(PersistentActorWithSnapshot("test-1"))
      (1 to 1000).foreach(i => actor ! Add(s"$i"))
      sleep(10.seconds)
    }
  }
}
