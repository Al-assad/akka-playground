package com.github.al.assad.akka.cluster.a_base

import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.cluster.typed.Cluster
import akka.util.Timeout
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

/**
 * Testing the actor behavior on cluster.
 */
trait ClusterActorTest {

  def launch(port: Int) = {
    val config = ConfigFactory
      .parseString(s"""akka.remote.artery.canonical.port=$port""".stripMargin)
      .withFallback(ConfigFactory.load("cluster-base"))
    val system = ActorSystem[DotterBot.Command](DotterBot(), "ClusterSystem", config)
    system -> Cluster(system)
  }
}


object DotterBot {

  sealed trait Command
  final case object Dot extends Command
  final case class Count(replyTo: ActorRef[Int]) extends Command
  private final case class WrappedCounterGetRes(value: Int, replyTo: ActorRef[Int]) extends Command

  implicit val timeout: Timeout = 5.seconds

  def apply(): Behavior[Command] = Behaviors.setup { ctx =>
    val counter = ctx.spawn(Counter(), "Counter")

    Behaviors.receiveMessage {
      case Dot =>
        counter ! Counter.Increment(ctx.system.address.toString)
        Behaviors.same

      case Count(replyTo) =>
        ctx.ask(counter, reply => Counter.Get(ctx.system.address.toString, reply)) {
          case Success(count) => WrappedCounterGetRes(count, replyTo)
          case Failure(ex) => throw ex
        }
        Behaviors.same

      case WrappedCounterGetRes(count, replyTo) =>
        replyTo ! count
        Behaviors.same
    }
  }
}


object Counter {
  sealed trait Command
  final case class Increment(from: String) extends Command
  final case class Get(from: String, replyTo: ActorRef[Int]) extends Command

  def apply(): Behavior[Command] = Behaviors.setup { ctx =>
    val log = ctx.log
    log.info(s"@Counter[${ctx.system.address}] setup")
    var value: Int = 0

    Behaviors.receiveMessage {
      case Increment(from) =>
        log.info(s"@Counter[${ctx.system.address.toString}] receive increment from $from")
        value += 1
        Behaviors.same
      case Get(from, replyTo) =>
        log.info(s"@Counter[${ctx.system.address.toString}] receive get from $from")
        replyTo ! value
        Behaviors.same
    }
  }
}

/**
 * There is a Counter on each actor system.
 */
//noinspection DuplicatedCode
object ClusterActorTest extends App with ClusterActorTest {

  val (system1, cluster1) = launch(25251)
  val (system2, cluster2) = launch(25252)
  val (system3, cluster3) = launch(0)

  waitClusterUp(cluster1, cluster2, cluster3)
  printMembers(cluster1)

  implicit val timeout: Timeout = 5.seconds
  implicit val defSystem = system1
  implicit val defScheduler = defSystem.scheduler
  implicit val defEc = defSystem.executionContext

  // tell from system1
  println("@Action system1 tell Dot twice")
  system1 ! DotterBot.Dot
  system1 ! DotterBot.Dot

  // ask result from system1
  system1 ? (ref => DotterBot.Count(ref)) onComplete {
    case Success(count) =>
      println(s"@Action system1 get Count: $count")
      assert(count == 2)  // count = 2
  }

  // ask result from system2
  system2 ? (ref => DotterBot.Count(ref)) onComplete {
    case Success(count) =>
      println(s"@Action system2 get Count: $count")
      assert(count == 0)  // count = 0
  }

  // tell from system2
  println("@Action system2 tell Dot")
  system2 ! DotterBot.Dot
  system1 ? (ref => DotterBot.Count(ref)) onComplete { case Success(count) =>
      println(s"@Action system1 get Count: $count")
      assert(count == 2)  // count = 2
  }
  system2 ? (ref => DotterBot.Count(ref)) onComplete { case Success(count) =>
    println(s"@Action system2 get Count: $count")
    assert(count == 1)  // count = 1
  }

}


