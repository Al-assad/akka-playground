package com.github.al.assad.akka.Actor.c_feature

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.{Behaviors, Routers}
import akka.actor.typed.{Behavior, SupervisorStrategy}
import com.github.al.assad.akka.uuidShort
import org.scalatest.wordspec.AnyWordSpecLike

/**
 * Actor router sample.
 * https://doc.akka.io/docs/akka/current/typed/routers.html
 *
 * There are two kinds of routers included in Akka Typed:
 * - Pool Router
 * - Group Router
 */
//noinspection DuplicatedCode
object ActorRouter {

  object Worker {
    sealed trait Command
    case class DoTask(msg: String) extends Command

    def apply(): Behavior[Command] = Behaviors.setup { ctx =>
      val id = uuidShort(4)
      println(s"Worker-$id started")
      Behaviors.receiveMessage { case DoTask(msg) =>
        println(s"Worker-$id do task: $msg")
        Behaviors.same
      }
    }
  }


  /**
   * Pool Router:
   *
   * If a child is stopped the pool router removes it from its set of routees.
   * When the last child stops the router itself stops. To make a resilient router
   * that deals with failures the routee Behavior must be supervised.
   *
   * As actor children are always local the routees are never spread across a cluster with a pool router.
   */
  // Testing normal pool router
  object PoolWorkerManager {
    sealed trait Command
    final case class StartTask(msg: String) extends Command

    def apply(): Behavior[Command] = Behaviors.setup { ctx =>
      // define a pool and router
      val pool = Routers.pool(poolSize = 4) {
        Behaviors.supervise(Worker()).onFailure(SupervisorStrategy.restart)
      }
      val router = ctx.spawn(pool, "worker-pool")

      Behaviors.receiveMessage {
        case StartTask(msg) =>
          router ! Worker.DoTask(msg)
          Behaviors.same
      }
    }
  }

  // Testing broadcast pool router
  object BroadcastPoolWorkerManager {
    sealed trait Command
    final case class StartTask(msg: String) extends Command

    def apply(): Behavior[Command] = Behaviors.setup { ctx =>
      // define a broadcast pool
      val pool = Routers
        .pool(poolSize = 4)(Behaviors.supervise(Worker()).onFailure(SupervisorStrategy.restart))
        .withBroadcastPredicate(_ => true)
      val router = ctx.spawn(pool, "worker-broadcast-pool")

      Behaviors.receiveMessage {
        case StartTask(msg) =>
          router ! Worker.DoTask(msg)
          Behaviors.same
      }
    }
  }

  // Testing mix of normal pool and broadcast pool
  object MixedPoolWorkerManager {
    sealed trait Command
    final case class StartTask(msg: String) extends Command
    final case class StartBroadcastTask(msg: String) extends Command

    private class DoBroadcastTask(msg: String) extends Worker.DoTask(msg)

    def apply(): Behavior[Command] = Behaviors.setup { ctx =>
      // define a broadcast pool
      val pool = Routers
        .pool(poolSize = 4)(Behaviors.supervise(Worker()).onFailure(SupervisorStrategy.restart))
        .withBroadcastPredicate(_.isInstanceOf[DoBroadcastTask])
      val router = ctx.spawn(pool, "worker-mixed-pool")

      Behaviors.receiveMessage {
        case StartTask(msg) =>
          router ! Worker.DoTask(msg)
          Behaviors.same
        case StartBroadcastTask(msg) =>
          router ! new DoBroadcastTask(msg)
          Behaviors.same
      }
    }
  }


  /**
   * Group Router:
   *
   * The group router is created with a ServiceKey and uses the receptionist to
   * discover available actors for that key and routes messages to one of the
   * currently known registered actors for a key.
   *
   * As actor children are always local the routees are never spread across
   * a cluster with a pool router. The router sends messages to registered actors
   * on any node in the cluster that is reachable.
   */
  object DoWorker {
    trait Command
    case class DoTask(msg: String) extends Command
    val WorkerServiceKey = ServiceKey[DoWorker.Command]("worker-service")

    def setup(tag: String): Behavior[Command] = Behaviors.setup { _ =>
      println(s"$tag started")
      Behaviors.receiveMessage { case DoTask(msg) =>
        println(s"$tag do task: $msg")
        Behaviors.same
      }
    }
  }

  object WorkerTypeA {
    import DoWorker._
    def apply(): Behavior[Command] = setup("WorkerTypeA")
  }
  object WorkerTypeB{
    import DoWorker._
    def apply(): Behavior[Command] = setup("WorkerTypeB")
  }

  // Testing group router
  object GroupWorkerManager {
    sealed trait Command
    final case class StartTask(msg: String) extends Command

    def apply(): Behavior[Command] = Behaviors.setup { ctx =>
      val workerA = ctx.spawn(WorkerTypeA(), "worker-A")
      val workerB = ctx.spawn(WorkerTypeB(), "worker-B")
      ctx.system.receptionist ! Receptionist.Register(DoWorker.WorkerServiceKey, workerA)
      ctx.system.receptionist ! Receptionist.Register(DoWorker.WorkerServiceKey, workerB)

      // define group with ServiceKey
      val group = Routers.group(DoWorker.WorkerServiceKey).withRoundRobinRouting()
      val router = ctx.spawn(group, "worker-group")

      Behaviors.receiveMessage {
        case StartTask(msg) =>
          router ! DoWorker.DoTask(msg)
          Behaviors.same
      }
    }
  }


}


class ActorRouterSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  import ActorRouter._

  "Pool Router" should {
    "PoolWorkerManager: testing pool router" in {
      val manager = spawn(PoolWorkerManager())
      (1 to 8) foreach (i => manager ! PoolWorkerManager.StartTask(s"task-$i"))
    }
    "BroadcastPoolWorkerManager: testing broadcast pool router" in {
      val manager = spawn(BroadcastPoolWorkerManager())
      manager ! BroadcastPoolWorkerManager.StartTask("task-a")
    }
    "MixedPoolWorkerManager: testing mixed pool router" in {
      val manager = spawn(MixedPoolWorkerManager())
      manager ! MixedPoolWorkerManager.StartTask("task-a")
      manager ! MixedPoolWorkerManager.StartBroadcastTask("task-b")
    }
  }

  "Group Router" should {
    "GroupWorkerManager: testing group router" in {
      val manager = spawn(GroupWorkerManager())
      (1 to 8) foreach (i => manager ! GroupWorkerManager.StartTask(s"task-$i"))
    }
  }


}

