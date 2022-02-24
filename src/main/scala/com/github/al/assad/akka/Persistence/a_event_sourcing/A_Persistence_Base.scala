package com.github.al.assad.akka.Persistence.a_event_sourcing

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.{ActorRef, Behavior}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}
import com.github.al.assad.akka.Persistence.inmenBackendConf
import org.scalatest.wordspec.AnyWordSpecLike

/**
 * Akka persistence simple sample (event sourcing).
 *
 * https://doc.akka.io/docs/akka/current/typed/persistence.html
 */
//noinspection DuplicatedCode
object PersistenceBase {

  /**
   * A simple persistent actor.
   */
  object SimplePersistentActor {
    // CQRS Command
    sealed trait Command
    final case class Add(data: String) extends Command
    case object Clear extends Command
    final case class GetHistory(replyTo: ActorRef[Seq[String]]) extends Command

    // CQRS Event
    sealed trait Event
    final case class Added(data: String) extends Event
    case object Cleared extends Event

    // CQRS State of the actor
    final case class State(dataHistory: Seq[String])

    // command handler
    def commandHandler: (State, Command) => Effect[Event, State] = { (state, command) =>
      command match {
        case Add(data) => Effect.persist(Added(data))
        case Clear => Effect.persist(Cleared)
        case GetHistory(replyTo) =>
          replyTo ! state.dataHistory
          Effect.none
      }
    }

    // event handler
    def eventHandler: (State, Event) => State = { (state, event) =>
      event match {
        case Added(data) => state.copy(dataHistory = state.dataHistory :+ data)
        case Cleared => State(Seq.empty)
      }
    }

    def apply(id: String): Behavior[Command] = EventSourcedBehavior[Command, Event, State](
      persistenceId = PersistenceId.ofUniqueId(id),
      emptyState = State(Seq.empty),
      commandHandler = commandHandler,
      eventHandler = eventHandler)
  }

}


class PersistenceBaseSpec extends ScalaTestWithActorTestKit(inmenBackendConf) with AnyWordSpecLike {
  import PersistenceBase._

  "Akka Persistence" should {

    "SimplePersistentActor" in {
      import PersistenceBase.SimplePersistentActor._
      val actor = spawn(SimplePersistentActor("test-1"))

      actor ! Add("a")
      actor ! Add("b")

      val probe = createTestProbe[Seq[String]]()
      actor ! GetHistory(probe.ref)
      probe.expectMessage(Seq("a", "b"))

      actor ! Clear
      actor ! GetHistory(probe.ref)
      probe.expectMessage(Seq.empty)
    }
  }


}
