package com.github.al.assad.akka.cluster.a_base

import akka.actor.typed._
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.MemberStatus
import akka.cluster.typed.{ClusterSingleton, SingletonActor}
import akka.util.Timeout
import com.github.al.assad.akka.BehaviorLoggerImplicit._
import com.github.al.assad.akka.cluster.CborSerializable
import com.github.al.assad.akka.cluster.a_base.ClusterActorSingletonTest1.printDotterGetResult
import com.github.al.assad.akka.sleep
import com.typesafe.config.ConfigFactory

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration.DurationInt
import scala.language.{implicitConversions, postfixOps}
import scala.util.{Failure, Success}

/**
 * Akka cluster singleton sample.
 * https://doc.akka.io/docs/akka/current/typed/cluster-singleton.html
 *
 * Make sure to not use a Cluster downing strategy that may split the cluster into several
 * separate clusters in case of network problems or system overload (long GC pauses),
 * since that will result in in multiple Singletons being started, one in each separate cluster!
 */
trait ClusterActorSingletonTest {

  def launch(port: Int): ActorSystem[SingletonDotterGuardian.Command] = {
    val config = ConfigFactory
      .parseString(
        s"""akka.remote.artery.canonical.port= $port""".stripMargin)
      .withFallback(ConfigFactory.load("cluster-base"))
    ActorSystem[SingletonDotterGuardian.Command](SingletonDotterGuardian(), "ClusterSystem", config)
  }


}


object SingletonDotterGuardian {
  sealed trait Command

  def apply(): Behavior[Command] = Behaviors.setup { ctx =>
    // create singleton manager and supervise Dotter
    val singletonManager = ClusterSingleton(ctx.system)
    val dotterProxy = singletonManager.init(
      SingletonActor(
        Behaviors
          .supervise(Dotter())
          .onFailure[Exception](SupervisorStrategy.restart),
        "singleton-dotter"))

    ctx.watch(dotterProxy)
    Behaviors
      .receiveMessage[Command] {
        case cmd: Dotter.Command =>
          dotterProxy ! cmd
          Behaviors.same
      }
      .receiveSignal {
        case (ctx, Terminated(`dotterProxy`)) =>
          ctx.log.info(s"@DotterGuardian singleton-dotter terminated on Node[${ctx.system.address}]")
          Behaviors.same
      }
  }
}

object Dotter {
  sealed trait Command extends SingletonDotterGuardian.Command
  final case object Dot extends Command with CborSerializable
  final case class Get(replyTo: ActorRef[Int]) extends Command with CborSerializable
  final case class WhereAreYou(replyTo: ActorRef[String]) extends Command with CborSerializable

  def apply(): Behavior[Command] = Behaviors.setup { implicit ctx =>
    log.info(s"@Dotter[${ctx.system.address}] setup")

    var value = 0
    Behaviors
      .receiveMessage[Command] {
        case Dot =>
          log.info(s"@Dotter receive Dot in Node[${ctx.system.address}]")
          value += 1
          Behaviors.same
        case Get(replyTo) =>
          log.info(s"@Dotter receive Get in Node[${ctx.system.address}]")
          replyTo ! value
          Behaviors.same
        case WhereAreYou(replyTo) =>
          replyTo ! ctx.system.address.toString
          Behaviors.same
      }
      .receiveSignal {
        case (context, PreRestart) =>
          log.info(s"@Dotter[${context.system.address}] restart")
          Behaviors.same
        case (context, PostStop) =>
          log.info(s"@Dotter[${context.system.address}] stopped")
          Behaviors.same
      }
  }
}

/**
 * Testing send event to singleton actor.
 */
object ClusterActorSingletonTest1 extends App with ClusterActorSingletonTest {
  val (system1, system2, system3) = (launch(25251), launch(25252), launch(25253))
  waitSystemUp(system1, system2, system3).printState

  println("@Actor system1 ! Dot")
  system1 ! Dotter.Dot
  println("@Actor system2 ! Dot")
  system2 ! Dotter.Dot
  println("@Actor system3 ! Dot")
  system3 ! Dotter.Dot

  def printDotterGetResult(system: ActorSystem[SingletonDotterGuardian.Command]) = {
    implicit val timeout: Timeout = 5.seconds
    implicit val ec: ExecutionContext = system.executionContext
    implicit val scheduler = system.scheduler

    system ? (ref => Dotter.Get(ref)) onComplete {
      case Success(value) => println(s"@Message ${system.address} ? Get => value: $value")
      case Failure(ex) => throw ex
    }
  }

  printDotterGetResult(system1) // maybe 1
  printDotterGetResult(system2) // 3
  printDotterGetResult(system3) // 3

  sleep(3.seconds)
  printDotterGetResult(system1) // 3
}

/**
 * Testing failover of singleton actor.
 */
object ClusterActorSingletonTest2 extends App with ClusterActorSingletonTest {
  val (system1, system2, system3) = (launch(25251), launch(25252), launch(25253))
  waitSystemUp(system1, system2, system3).printState

  def printWhereIsDotter(system: ActorSystem[SingletonDotterGuardian.Command]) = {
    implicit val timeout: Timeout = 30.seconds
    implicit val ec: ExecutionContext = system.executionContext
    implicit val scheduler = system.scheduler

    val f = system ? (ref => Dotter.WhereAreYou(ref))
    f onComplete {
      case Success(value) => println(s"@Message Dotter on node: $value")
      case Failure(ex) => throw ex
    }
    Await.result(f, 30.seconds)
  }

  println("@Action system1 ! Dot")
  system1 ! Dotter.Dot
  println("@Action system2 ! Dot")
  system2 ! Dotter.Dot
  println("@Action system3 ! Dot")
  system3 ! Dotter.Dot

  // print the node address of Dotter actor
  printWhereIsDotter(system2) // system1
  sleep(3.seconds)
  printDotterGetResult(system1) // 3
  printDotterGetResult(system2) // 3
  printDotterGetResult(system3) // 3

  // terminate system1, the Dotter will transfer to system2
  println("@Action terminate system1")
  system1.terminate()
  waitSystemUnit(MemberStatus.Removed, system1)
  sleep(3.seconds)
  printMembers(system2)

  printWhereIsDotter(system2)

  println("@Action system3 ! Dotter.Dot")
  system3 ! Dotter.Dot
  printDotterGetResult(system2) // 1
}
