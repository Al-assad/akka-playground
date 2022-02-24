package com.github.al.assad.akka.Persistence.a_event_sourcing

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import com.github.al.assad.akka.Persistence.a_event_sourcing.PersistenceStash.SingleTaskScheduler
import com.github.al.assad.akka.Persistence.inmenBackendConf
import org.scalatest.BeforeAndAfterEach
import org.scalatest.wordspec.AnyWordSpecLike

import scala.language.postfixOps

/**
 * Akka Persistence TestKit use case.
 * Testing the [[SingleTaskScheduler]] Persistence Actor.
 *
 * https://doc.akka.io/docs/akka/current/typed/persistence-testing.html
 */
//noinspection ScalaFileName
class SingleTaskSchedulerTestKitSpec extends ScalaTestWithActorTestKit(EventSourcedBehaviorTestKit.config.withFallback(inmenBackendConf))
    with AnyWordSpecLike
    with BeforeAndAfterEach {


  // Create a EventSourcedTestKit from SingleTaskScheduler Actor
  private val eventSourcedTestKit = EventSourcedBehaviorTestKit[SingleTaskScheduler.Command, SingleTaskScheduler.Event, SingleTaskScheduler.State](
    system, SingleTaskScheduler("test-1"))

  override def beforeEach(): Unit = {
    super.beforeEach()
    // before each test, we need to clear the journal
    eventSourcedTestKit.clear()
  }

  "SingleTaskScheduler" should {

    import SingleTaskScheduler._

    "behave normally" in {
      val result = eventSourcedTestKit.runCommand(StartTask("task-1"))
      result.event shouldBe TaskStarted("task-1")
      result.state shouldBe State(Some("task-1"))

      val result2 = eventSourcedTestKit.runCommand(NextStep("task-1", "step-a"))
      result2.event shouldBe TaskStep("task-1", "step-a")
      result2.state shouldBe State(Some("task-1"))

      val result3 = eventSourcedTestKit.runCommand(EndTask("task-1"))
      result3.event shouldBe TaskCompleted("task-1")
    }

    "behave when command been dropped" in {
      val result = eventSourcedTestKit.runCommand(NextStep("task-1", "step-a"))
      result.hasNoEvents shouldBe true

      val result2 = eventSourcedTestKit.runCommand(EndTask("task-2"))
      result2.hasNoEvents shouldBe true
    }

    "behave when command been stashed" in {
      eventSourcedTestKit.runCommand(StartTask("task-1"))
      eventSourcedTestKit.runCommand(StartTask("task-2")).hasNoEvents shouldBe true
      eventSourcedTestKit.runCommand(NextStep("task-3", "step-a")).hasNoEvents shouldBe true
      eventSourcedTestKit.runCommand(EndTask("task-2")).hasNoEvents shouldBe true

      val result = eventSourcedTestKit.runCommand(EndTask("task-1"))
      result.event shouldBe TaskCompleted("task-1")
      result.events shouldBe Seq(TaskCompleted("task-1"), TaskStarted("task-2"), TaskCompleted("task-2"))
    }

  }
}
