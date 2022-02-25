package com.github.al.assad.akka.Persistence.a_event_sourcing

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed._
import akka.pattern.StatusReply
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}
import akka.persistence.typed.{PersistenceId, RecoveryCompleted}
import com.github.al.assad.akka.Persistence.{CborSerializable, inmenBackendConf}
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

/**
 * Akka persistence use case sample (event sourcing), including:
 *
 * - Effect Behavior
 * - ReceiveSignal from EventSourcedBehavior
 * - Replies
 * - Supervisor strategy on Persist Failure
 *
 * https://doc.akka.io/docs/akka/current/typed/persistence.html
 */
//noinspection DuplicatedCode
object PersistenceUseCase {


  trait BasePersistentActor {
    // CQRS Command
    sealed trait Command extends CborSerializable
    final case class Add(data: String) extends Command
    case object Clear extends Command
    final case class GetHistory(replyTo: ActorRef[Seq[String]]) extends Command

    // CQRS Event
    sealed trait Event extends CborSerializable
    final case class Added(data: String) extends Event
    case object Cleared extends Event

    // CQRS State of the actor
    final case class State(dataHistory: Seq[String]) extends CborSerializable

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

    def defaultEventSourcedBehavior(id: String) = EventSourcedBehavior[Command, Event, State](
      persistenceId = PersistenceId.ofUniqueId(id),
      emptyState = State(Seq.empty),
      commandHandler = commandHandler,
      eventHandler = eventHandler)

  }

  /**
   * Use case: Effect behavior and receiveSignal.
   */
  object PersistentActorWithSideEffect extends BasePersistentActor {
    // command handler
    override def commandHandler: (State, Command) => Effect[Event, State] = { (state, command) =>
      command match {
        case Add(data) =>
          Effect
            .persist(Added(data))
            .thenRun(state => println(s"Added: $data, current state: $state"))
        case Clear =>
          Effect.persist(Cleared).thenStop
        case GetHistory(replyTo) =>
          replyTo ! state.dataHistory
          Effect.none
      }
    }

    // using ActorContext directly via Behaviors.setup
    def apply(id: String): Behavior[Command] = Behaviors.setup { ctx =>
      println("PersistentActorWithEffectSample start")

      EventSourcedBehavior[Command, Event, State](
        persistenceId = PersistenceId.ofUniqueId(id),
        emptyState = State(Seq.empty),
        commandHandler = commandHandler,
        eventHandler = eventHandler)
        // receiveSignal from EventSourcedBehavior
        .receiveSignal {
          case (state, RecoveryCompleted) =>
            ctx.log.info("PersistentActorWithEffectSample recovery completed")
          case (state, PostStop) =>
            println("PersistentActorWithEffectSample stopped")
        }
    }
  }

  /**
   * Use case: Reply from EventSourcedBehavior.
   */
  object PersistentActorWithReplies extends BasePersistentActor {
    final case class AddWithReply(data: String, replyTo: ActorRef[StatusReply[AddDone]]) extends Command
    final case class AddDone(idx: Int)

    // command handler
    override def commandHandler: (State, Command) => Effect[Event, State] = { (state, command) =>
      command match {
        case Add(data) => Effect.persist(Added(data))
        case Clear => Effect.persist(Cleared)
        case GetHistory(replyTo) =>
          replyTo ! state.dataHistory
          Effect.none
        // reply on command
        case AddWithReply(data, replyTo) =>
          Effect.persist(Added(data))
            .thenReply(replyTo)(_ => StatusReply.Success(AddDone(state.dataHistory.size)))
      }
    }

    def apply(id: String) = defaultEventSourcedBehavior(id)
  }


  /**
   * Use case: Supervisor strategy on Persist Failure
   */
  object PersistentActorWithSupervisor extends BasePersistentActor {
    final case object PoisonPill extends Command

    override def commandHandler: (State, Command) => Effect[Event, State] = { (state, command) =>
      command match {
        case Add(data) => Effect.persist(Added(data))
        case Clear => Effect.persist(Cleared)
        case GetHistory(replyTo) =>
          replyTo ! state.dataHistory
          Effect.none
        case PoisonPill =>
          throw new RuntimeException("Boom!")
        //          Effect.stop
      }
    }

    def apply(id: String): Behavior[Command] = Behaviors.supervise(
      EventSourcedBehavior[Command, Event, State](
        persistenceId = PersistenceId.ofUniqueId(id),
        emptyState = State(Seq.empty),
        commandHandler = commandHandler,
        eventHandler = eventHandler)
        // restart strategy on Persist Failure
        .onPersistFailure(SupervisorStrategy.restartWithBackoff(
          minBackoff = 2.seconds, maxBackoff = 5.seconds, randomFactor = 0.2))
        .receiveSignal {
          case (_, PostStop) => println("actor stopped")
          case (_, PreRestart) => println("actor restarting")
        }
    ).onFailure(SupervisorStrategy.restart)

  }

  /**
   * Use case: Tagging events
   */
  object PersistentActorWithTagging extends BasePersistentActor {
    def apply(id: String): Behavior[Command] =
      EventSourcedBehavior[Command, Event, State](
        persistenceId = PersistenceId.ofUniqueId(id),
        emptyState = State(Seq.empty),
        commandHandler = commandHandler,
        eventHandler = eventHandler)
        .withTagger {
          case Added(data) => if (data.nonEmpty) Set("add-ops", "ec-sample") else Set("add-empty", "ec-sample")
          case Cleared => Set("clear-ops", "ec-sample")
        }
  }

}


class PersistenceUseCaseSpec extends ScalaTestWithActorTestKit(inmenBackendConf) with AnyWordSpecLike {

  import PersistenceUseCase._

  "Akka Persistence" should {

    "PersistentActorWithSideEffect" in {
      import PersistenceUseCase.PersistentActorWithSideEffect._
      val actor = spawn(PersistentActorWithSideEffect("test-1"))
      val probe = createTestProbe[Seq[String]]()

      actor ! Add("a")
      actor ! Add("b")
      actor ! GetHistory(probe.ref)
      probe.expectMessage(Seq("a", "b"))
      actor ! Clear
      probe.expectTerminated(actor)
    }

    "PersistentActorWithReplies" in {
      import PersistenceUseCase.PersistentActorWithReplies._
      val actor = spawn(PersistentActorWithReplies("test-1"))
      val probe = createTestProbe[StatusReply[AddDone]]()

      actor ! AddWithReply("a", probe.ref)
      probe.receiveMessage.getValue shouldBe AddDone(0)

      actor ! AddWithReply("b", probe.ref)
      probe.receiveMessage.getValue shouldBe AddDone(1)
    }

    "PersistentActorWithSupervisor" in {
      import PersistenceUseCase.PersistentActorWithSupervisor._
      val actor = spawn(PersistentActorWithSupervisor("test-1"))
      val watch = createTestProbe[ActorRef[Command]]()
      actor ! PoisonPill
      assertThrows[AssertionError](watch.expectTerminated(actor))
    }

  }
}
