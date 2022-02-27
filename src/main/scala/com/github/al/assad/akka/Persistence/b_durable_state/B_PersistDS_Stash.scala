package com.github.al.assad.akka.Persistence.b_durable_state


import akka.actor.typed.{Behavior, SupervisorStrategy}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.state.scaladsl.{DurableStateBehavior, Effect}
import com.github.al.assad.akka.Persistence.{AkkaPersistenceJdbcTestKit, CborSerializable, jdbcBackendJsonSerConf}
import com.github.al.assad.akka.sleep

import scala.concurrent.duration.DurationInt

/**
 * Akka Persistence Stash Example (Durable State).
 * This is the Durable State version of [[com.github.al.assad.akka.Persistence.a_event_sourcing.PersistenceStash.SingleTaskScheduler]]
 */
object PersistDSStash {

  //noinspection DuplicatedCode
  object SingleTaskScheduler {

    sealed trait Command extends CborSerializable
    final case class StartTask(taskId: String) extends Command
    final case class NextStep(taskId: String, instruction: String) extends Command
    final case class EndTask(taskId: String) extends Command

    final case class State(taskIdInProcess: Option[String]) extends CborSerializable

    // command handler
    private def onCommand(state: State, command: Command): Effect[State] = {
      state.taskIdInProcess match {
        case None => command match {
          // when no task is in process, only receive StartTask command
          case StartTask(taskId) =>
            Effect.persist(State(Option(taskId))).thenRun(_ => s"taskId[$taskId] => started")
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
              Effect.persist(state).thenRun(_ => println(s"taskId[$taskId] => go to next step: $instruction"))
            } else {
              println(s"$command is stashed because task[$inProgress] is in progress")
              Effect.stash // other task in progress, wait with new task until later
            }

          case EndTask(taskId) =>
            if (inProgress == taskId) {
              println(s"taskId[$taskId] => completed")
              Effect.persist(State(None)).thenUnstashAll() // unstash all command
            } else {
              println(s"$command is stashed because task[$inProgress] is in progress")
              Effect.stash() //  other task in progress, wait with new task until later
            }
        }
      }
    }

    def apply(id: String): Behavior[Command] = DurableStateBehavior[Command, State](
      persistenceId = PersistenceId.ofUniqueId(id),
      emptyState = State(None),
      commandHandler = onCommand)
      .onPersistFailure(SupervisorStrategy.restartWithBackoff(1.second, 10.seconds, 0.1))
  }
}


//noinspection DuplicatedCode
class PersistDSStashSpec extends AkkaPersistenceJdbcTestKit(jdbcBackendJsonSerConf) {

  import PersistDSStash._
  import PersistDSStash.SingleTaskScheduler._

  "SingleTaskScheduler" should {
    "behave normally" in {
      val actor = spawn(SingleTaskScheduler("single-task-scheduler-ds"))
      actor ! StartTask("task-1")
      actor ! NextStep("task-1", "step-1")
      actor ! NextStep("task-1", "step-2")
      actor ! EndTask("task-1")
      sleep(3.seconds)
    }
    "behave when command been dropped" in {
      val actor = spawn(SingleTaskScheduler("single-task-scheduler-ds"))
      actor ! NextStep("task-1", "step-1")
      actor ! NextStep("task-2", "step-1")
      actor ! EndTask("task-2")
      sleep(3.seconds)
    }
    "behave when command been stashed" in {
      val actor = spawn(SingleTaskScheduler("single-task-scheduler-ds"))
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

