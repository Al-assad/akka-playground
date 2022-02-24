package com.github.al.assad.akka.Persistence.a_event_sourcing

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.{Behavior, SupervisorStrategy}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}
import com.github.al.assad.akka.Cluster.CborSerializable
import com.github.al.assad.akka.Persistence.inmenBackendConf
import com.github.al.assad.akka.sleep
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration.DurationInt

/**
 * Akka Persistence Stash Example (Event Sourcing).
 *
 * https://doc.akka.io/docs/akka/current/typed/persistence.html#stash
 */
object PersistenceStash {

  /**
   * The SingleTaskScheduler always allow only one task to be running.
   */
  object SingleTaskScheduler {
    sealed trait Command extends CborSerializable
    final case class StartTask(taskId: String) extends Command
    final case class NextStep(taskId: String, instruction: String) extends Command
    final case class EndTask(taskId: String) extends Command

    sealed trait Event extends CborSerializable
    final case class TaskStarted(taskId: String) extends Event
    final case class TaskStep(taskId: String, instruction: String) extends Event
    final case class TaskCompleted(taskId: String) extends Event

    final case class State(taskIdInProcess: Option[String]) extends CborSerializable


    // command handler
    private def onCommand(state: State, command: Command): Effect[Event, State] = {
      state.taskIdInProcess match {
        case None => command match {
          // when no task is in process, only receive StartTask command
          case StartTask(taskId) =>
            Effect.persist(TaskStarted(taskId)).thenRun(_ => s"taskId[$taskId] => started")
          case _ =>
            println(s"$command is drooped because no task is in process")
            Effect.unhandled
        }
        case Some(inProgress) => command match {
          case StartTask(taskId) =>
            if (inProgress == taskId) {
              println(s"task[$taskId] is already in progress")
              Effect.none // duplicate, the task is already in progress
            } else {
              println(s"$command is stashed because task[$inProgress] is in progress")
              Effect.stash // other task in progress, wait with new task until later
            }

          case NextStep(taskId, instruction) =>
            if (inProgress == taskId) {
              Effect.persist(TaskStep(taskId, instruction)).thenRun(_ => println(s"taskId[$taskId] => go to next step: $instruction"))
            } else {
              println(s"$command is stashed because task[$inProgress] is in progress")
              Effect.stash // other task in progress, wait with new task until later
            }

          case EndTask(taskId) =>
            if (inProgress == taskId) {
              println(s"taskId[$taskId] => completed")
              Effect.persist(TaskCompleted(taskId)).thenUnstashAll()   // unstash all command
            } else {
              println(s"$command is stashed because task[$inProgress] is in progress")
              Effect.stash() //  other task in progress, wait with new task until later
            }
        }
      }
    }

    // event handler
    private def onEvent(state: State, event: Event): State = {
      event match {
        case TaskStarted(taskId) => State(Option(taskId))
        case TaskStep(taskId, instruction) => state
        case TaskCompleted(taskId) => State(None)
      }
    }

    def apply(persistId: String): Behavior[Command] = EventSourcedBehavior[Command, Event, State](
      persistenceId = PersistenceId.ofUniqueId(persistId),
      emptyState = State(None),
      commandHandler = (state, command) => onCommand(state, command),
      eventHandler = (state, event) => onEvent(state, event)
    ).onPersistFailure(SupervisorStrategy.restartWithBackoff(1.second, 10.seconds, 0.1))

  }

}

class PersistenceStashSpec extends ScalaTestWithActorTestKit(inmenBackendConf) with AnyWordSpecLike {

  import PersistenceStash._
  import SingleTaskScheduler._

  "SingleTaskScheduler" should {
    "behave normally" in {
      val actor = spawn(SingleTaskScheduler("single-task-scheduler"))
      actor ! StartTask("task-1")
      actor ! NextStep("task-1", "step-1")
      actor ! NextStep("task-1", "step-2")
      actor ! EndTask("task-1")
      sleep(3.seconds)
    }
    "behave when command been dropped" in {
      val actor = spawn(SingleTaskScheduler("single-task-scheduler"))
      actor ! NextStep("task-1", "step-1")
      actor ! NextStep("task-2", "step-1")
      actor ! EndTask("task-2")
      sleep(3.seconds)
    }
    "behave when command been stashed" in {
      val actor = spawn(SingleTaskScheduler("single-task-scheduler"))
      actor ! StartTask("task-1")
      actor ! StartTask("task-2")
      actor ! StartTask("task-3")

      actor ! NextStep("task-1", "step-1")
      actor ! NextStep("task-3", "step-1")
      actor ! NextStep("task-2", "step-1")

      actor ! EndTask("task-3")
      actor ! EndTask("task-2")
      actor ! EndTask("task-1")

      sleep(5.seconds)
    }

  }

}
