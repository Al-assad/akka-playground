package com.github.al.assad.akka.actor.c_feature

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, DispatcherSelector}
import com.github.al.assad.akka.sleep
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

/**
 * Actor dispatcher sample.
 *
 * https://doc.akka.io/docs/akka/current/typed/dispatchers.html
 *
 * There are 2 different types of message dispatchers:
 *
 * 1. Dispatcher:
 * This is an event-based dispatcher that binds a set of Actors to a thread pool.
 * The default dispatcher is used if no other is specified.
 *
 * 2. PinnedDispatcher:
 * This dispatcher dedicates a unique thread for each actor using it;
 * i.e. each actor will have its own thread pool with only one thread in the pool.
 *
 * more dispatcher configuration: https://doc.akka.io/docs/akka/current/typed/dispatchers.html#more-dispatcher-configuration-examples
 *
 */


// The following demonstrates a sample of Blocking Actor with standalone blocking dispatcher.

/**
 * Problem: Blocking on default dispatcher
 */
object BlockActorProblem {
  // blocking actor
  object BlockingActor {
    def apply(): Behavior[Int] = Behaviors.receiveMessage { i =>
      sleep(5.seconds)
      println(s"Blocking Actor => operation finished: $i")
      Behaviors.same
    }
  }
  // normal actor
  object PrintActor {
    def apply(): Behavior[Integer] = Behaviors.receiveMessage { i =>
      println(s"PrintActor: $i")
      Behaviors.same
    }
  }

  def main(args: Array[String]): Unit = {
    val root = Behaviors.setup[Nothing] { ctx =>
      for (i <- 1 to 1000) {
        ctx.spawn(BlockingActor(), s"blocking-actor-$i") ! i
        ctx.spawn(PrintActor(), s"print-actor-$i") ! i
      }
      Behaviors.empty
    }
    ActorSystem[Nothing](root, "BlockActorProblem")
    // The PrintActor is affected by BlockingActor and executes very slowly.
  }
}


/**
 * Non-solution: Wrapping in a Future
 */
//noinspection DuplicatedCode
object BlockActorWrappingFuture {

  object BlockingFutureActor {
    def apply(): Behavior[Int] = Behaviors.setup { ctx =>
      implicit val executionContext: ExecutionContext = ctx.executionContext

      Behaviors.receiveMessage { i =>
        println(s"Blocking Actor => Calling blocking Future: $i")
        Future {
          sleep(5.seconds)
          println(s"Blocking Actor => Blocking future finished $i")
        }
        Behaviors.same
      }
    }
  }

  import BlockActorProblem.PrintActor

  def main(args: Array[String]): Unit = {
    val root = Behaviors.setup[Nothing] { ctx =>
      for (i <- 1 to 1000) {
        ctx.spawn(BlockingFutureActor(), s"blocking-future-actor-$i") ! i
        ctx.spawn(PrintActor(), s"print-actor-$i") ! i
      }
      Behaviors.empty
    }
    ActorSystem[Nothing](root, "BlockActorWrappingFuture")
    // PrintActor is a bit faster than BlockActorProblem sample,
    // but it still doesn't solve the problem completely.
  }
}


/**
 * Solution: Dedicated dispatcher for blocking operations
 */
//noinspection DuplicatedCode
object BlockActorWithDedicatedDispatcher {

  object DedicatedBlockingActor {
    def apply(): Behavior[Int] = Behaviors.setup { ctx =>
      // using default blocking dispatcher
      implicit val executionContext: ExecutionContext =
        ctx.system.dispatchers.lookup(DispatcherSelector.fromConfig("my-blocking-dispatcher"))

      Behaviors.receiveMessage { i =>
        println(s"Blocking Actor => Calling blocking Future: $i")
        Future {
          sleep(5.seconds)
          println(s"Blocking Actor => Blocking future finished $i")
        }
        Behaviors.same
      }
    }
  }

    import BlockActorProblem.PrintActor

    def main(args: Array[String]): Unit = {
      val root = Behaviors.setup[Nothing] { ctx =>
        for (i <- 1 to 1000) {
          ctx.spawn(DedicatedBlockingActor(), s"blocking-future-actor-$i") ! i
          ctx.spawn(PrintActor(), s"print-actor-$i") ! i
        }
        Behaviors.empty
      }
      val config = ConfigFactory.parseString(
        """
          |my-blocking-dispatcher {
          |  type = Dispatcher
          |  executor = "thread-pool-executor"
          |  thread-pool-executor {
          |    fixed-pool-size = 16
          |  }
          |  throughput = 1
          |}
          |""".stripMargin)
      ActorSystem[Nothing](root, "BlockActorWithDedicatedDispatcher", config)
    }
}
