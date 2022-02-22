package com.github.al.assad.akka.actor.c_feature

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.scaladsl.{Behaviors, Routers}
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import com.github.al.assad.akka.uuidShort
import org.scalatest.wordspec.AnyWordSpecLike

import scala.language.postfixOps

/**
 * Actor router routing strategy sample.
 *
 * https://doc.akka.io/docs/akka/current/typed/routers.html#routing-strategies
 */
object ActorRouterStrategy {

  import ActorRouter.Worker

  trait BasePoolWorkerManager {
    sealed trait Command
    final case class StartTask(msg: String) extends Command

    def receiveBehavior(router: ActorRef[Worker.Command]): Behavior[Command] = Behaviors.receiveMessage {
      case StartTask(msg) =>
        router ! Worker.DoTask(msg)
        Behaviors.same
    }
  }

  /**
   * Random Routing
   * Randomly selects a routee when a message is sent through the router.
   * This is the default for group routers as the group of routees is expected
   * to change as nodes join and leave the cluster.
   */
  object PoolRandomWorkerManager extends BasePoolWorkerManager {
    def apply(): Behavior[Command] = Behaviors.setup { ctx =>
      val pool = Routers
        .pool(poolSize = 2)(Behaviors.supervise(Worker()).onFailure(SupervisorStrategy.restart))
        .withRandomRouting()
      val router = ctx.spawn(pool, "worker-pool")
      receiveBehavior(router)
    }
  }

  /**
   * Round Robin
   * Rotates over the set of routees making sure that if there are n routees,
   * then for n messages sent through the router, each actor is forwarded one message.
   */
  object PoolRobinWorkerManager extends BasePoolWorkerManager {
    def apply(): Behavior[Command] = Behaviors.setup { ctx =>
      val pool = Routers
        .pool(poolSize = 2)(Behaviors.supervise(Worker()).onFailure(SupervisorStrategy.restart))
        .withRoundRobinRouting()
      val router = ctx.spawn(pool, "worker-pool")
      receiveBehavior(router)
    }
  }


  /**
   * Consistent Hashing
   *
   * Uses consistent hashing to select a routee based on the sent message.
   */
  //noinspection DuplicatedCode
  object TypedWorker {
    trait Command
    case class DoTask(typeId: Int, msg: String) extends Command

    def apply(): Behavior[Command] = Behaviors.setup { ctx =>
      val id = uuidShort(2)
      println(s"Worker-$id started")
      Behaviors.receiveMessage { case DoTask(typeId, msg) =>
        println(s"Worker-$id do task: typeId[$typeId], $msg")
        Behaviors.same
      }
    }
  }

  object PoolHashWorkerManager {
    sealed trait Command
    final case class StartTask(typeId: Int, msg: String) extends Command

    def apply(): Behavior[Command] = Behaviors.setup { ctx =>
      val pool = Routers
        .pool(poolSize = 4)(Behaviors.supervise(TypedWorker()).onFailure(SupervisorStrategy.restart))
        .withConsistentHashingRouting(
          virtualNodesFactor = 10,
          mapping = _.asInstanceOf[TypedWorker.DoTask].typeId.toString
        )
      val router = ctx.spawn(pool, "worker-pool")

      Behaviors.receiveMessage {
        case StartTask(typeId, msg) =>
          router ! TypedWorker.DoTask(typeId, msg)
          Behaviors.same
      }
    }
  }

}

class ActorRouterStrategySpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {
  import ActorRouterStrategy._

  "Actor Routing Strategy" should {
    "Random Routing" in {
      val manager = spawn(PoolRandomWorkerManager())
      (1 to 10) foreach (i => manager ! PoolRandomWorkerManager.StartTask(s"task-$i"))
    }
    "Round Robin Routing" in {
      val manager = spawn(PoolRobinWorkerManager())
      (1 to 10) foreach (i => manager ! PoolRobinWorkerManager.StartTask(s"task-$i"))
    }
    "Consistent Hashing Routing" in {
      val manager = spawn(PoolHashWorkerManager())
      manager ! PoolHashWorkerManager.StartTask(1, s"task-1-1")
      manager ! PoolHashWorkerManager.StartTask(2, s"task-2-1")
      manager ! PoolHashWorkerManager.StartTask(2, s"task-2-2")
      manager ! PoolHashWorkerManager.StartTask(1, s"task-1-2")
      manager ! PoolHashWorkerManager.StartTask(1, s"task-1-3")
      manager ! PoolHashWorkerManager.StartTask(3, s"task-3")
    }
  }
}

