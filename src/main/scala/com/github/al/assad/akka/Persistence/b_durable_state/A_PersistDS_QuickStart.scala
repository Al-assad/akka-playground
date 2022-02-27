package com.github.al.assad.akka.Persistence.b_durable_state

import akka.actor.typed.scaladsl.AskPattern.{Askable, schedulerFromActorSystem}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.state.scaladsl.{DurableStateBehavior, Effect}
import akka.util.Timeout
import com.github.al.assad.akka.Persistence.b_durable_state.PersistenceDurableStateQuickStart.PersistentActor
import com.github.al.assad.akka.Persistence.b_durable_state.PersistenceDurableStateQuickStart.PersistentActor.{Add, GetHistory}
import com.github.al.assad.akka.Persistence.{AkkaPersistenceJdbcTestKit, CborSerializable, SchemaGenerator, h2JdbcBackendConf, jdbcBackendJsonSerConf}
import com.github.al.assad.akka.TestImplicit.testProbe
import com.github.al.assad.akka.sleep

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

/**
 * Akka Persistence (Durable State) example.
 *
 * https://doc.akka.io/docs/akka/current/typed/durable-state/persistence.html
 *
 * This model of Akka Persistence enables a stateful actor / entity to store the full
 * state after processing each command instead of using event sourcing. This reduces
 * the conceptual complexity and can be a handy tool for simple use cases.
 *
 * Very much like a CRUD based operation, the API is conceptually simple -
 * a function from current state and incoming command to the next state which replaces
 * the current state in the database.
 */

object PersistenceDurableStateQuickStart {

  /**
   * A simple persistent actor, it's the durable state version of
   * [[com.github.al.assad.akka.Persistence.a_event_sourcing.PersistenceQuickStart.SimplePersistentActor]]
   */
  object PersistentActor {
    // command
    sealed trait Command extends CborSerializable
    final case class Add(data: String) extends Command
    case object Clear extends Command
    final case class GetHistory(replyTo: ActorRef[Seq[String]]) extends Command

    // state
    final case class State(dataHistory: Seq[String]) extends CborSerializable

    // command handler
    def onCommand: (State, Command) => Effect[State] = (state, command) =>
      command match {
        case Add(data) =>
          Effect.persist(state.copy(dataHistory = state.dataHistory :+ data))
            .thenRun(state => println(s"current data size: ${state.dataHistory.size}"))
        case Clear => Effect.persist(State(Seq.empty))
        case GetHistory(replyTo) => Effect.reply(replyTo)(state.dataHistory)
      }


    def apply(id: String): Behavior[Command] =
      DurableStateBehavior[Command, State](
        persistenceId = PersistenceId.ofUniqueId(id),
        emptyState = State(Seq.empty),
        commandHandler = onCommand)
  }

  def main(args: Array[String]): Unit = {
    SchemaGenerator.createSchema(h2JdbcBackendConf)
    implicit val system = ActorSystem(PersistentActor("test-ds-2"), "test", h2JdbcBackendConf)
    implicit val timeout: Timeout = 10.seconds
    system ! Add("a")
    system ! Add("b")
    system ? (ref => GetHistory(ref)) onComplete {
      case Success(history) => println(s"history: $history")
      case Failure(e) => println(s"error: $e")
    }
    sleep(10.seconds)
  }

}


//noinspection DuplicatedCode
class PersistenceDurableStateQuickStartSpec extends AkkaPersistenceJdbcTestKit(jdbcBackendJsonSerConf) {

  import PersistentActor._

  "SimplePersistentActor" should {

    "behave normally" in {
      val actor = spawn(PersistentActor("test-ds-2"))

      testProbe[Seq[String]] { probe =>
        actor ! Add("a")
        actor ! Add("b")

        actor ! GetHistory(probe.ref)
        probe.expectMessage(Seq("a", "b"))
        actor ! Clear
        actor ! GetHistory(probe.ref)
        probe.expectMessage(Seq.empty)
      }
    }
  }

}




