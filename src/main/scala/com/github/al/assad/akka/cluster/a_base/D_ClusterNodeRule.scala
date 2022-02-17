package com.github.al.assad.akka.cluster.a_base

import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.cluster.typed.Cluster
import akka.util.Timeout
import com.typesafe.config.ConfigFactory

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

/**
 * Testing node rule of akka cluster.
 */
trait ClusterNodeRuleTest {

  /**
   * When has role "listener", it will spawn a ClusterListenerGuardian actor.
   * When has role "dotter", it will spawn a DotterGuardian actor.
   */
  object RuleRootActor {
    sealed trait Command

    def apply(): Behavior[Command] = Behaviors.setup { ctx =>
      val selfMember = Cluster(ctx.system).selfMember

      val listener = Option {
        if (selfMember.hasRole("listener"))
          ctx.spawn(ClusterListenerGuardian(), "ClusterListenerGuardian")
        else null
      }
      val dotter = Option {
        if (selfMember.hasRole("dotter"))
          ctx.spawn(DotterGuardian(), "DotterGuardian")
        else null
      }

      Behaviors.receiveMessage {
        case cmd: ClusterListenerGuardian.Command => listener match {
          case Some(ref) =>
            ref ! cmd
            Behaviors.same
          case None =>
            ctx.log.error(s"ClusterListenerGuardian Actor not exist on {${selfMember.address}}")
            Behaviors.unhandled
        }
        case cmd: DotterGuardian.Command => dotter match {
          case Some(ref) =>
            ref ! cmd
            Behaviors.same
          case None =>
            ctx.log.error(s"DotterGuardian Actor not exist on {${selfMember.address}}")
            Behaviors.unhandled
        }
      }
    }
  }

  // Guardian actor for ClusterListener
  object ClusterListenerGuardian {
    sealed trait Command extends RuleRootActor.Command
    def apply(): Behavior[Command] = Behaviors.setup { ctx =>
      ctx.spawn(ClusterListener(), "ClusterListener")
      ctx.log.info(s"Node[${ctx.system.address}] spawn ClusterListener Actor")
      Behaviors.empty
    }
  }

  //noinspection DuplicatedCode
  // Guardian actor for Dotter
  object DotterGuardian {
    sealed trait Command extends RuleRootActor.Command
    final case object Dot extends Command
    final case class Count(replyTo: ActorRef[Int]) extends Command
    private final case class WrappedCounterGetRes(value: Int, replyTo: ActorRef[Int]) extends Command

    implicit val timeout: Timeout = 5.seconds

    def apply(): Behavior[Command] = Behaviors.setup { ctx =>
      val counter = ctx.spawn(Counter(), "Counter")
      ctx.log.info(s"Node[${ctx.system.address}] spawn Counter Actor")
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

  /**
   * launch the cluster with specific role and port
   */
  def launch(port: Int, roles: Array[String]): ActorSystem[RuleRootActor.Command] = {
    val config = ConfigFactory
      .parseString(
        s"""akka.remote.artery.canonical.port= $port
           |akka.cluster.roles = [${roles.mkString(",")}]
           |""".stripMargin)
      .withFallback(ConfigFactory.load("cluster-base"))

    ActorSystem[RuleRootActor.Command](RuleRootActor(), "ClusterSystem", config)
  }
}


/**
 * Testing App
 */
object ClusterNodeRuleTest1 extends App with ClusterNodeRuleTest {

  val system1 = launch(25251, Array("listener"))
  val system2 = launch(25252, Array("listener", "dotter"))
  val system3 = launch(25253, Array("listener"))

  waitSystemUp(system1, system2, system3)
  // print member of all nodes
  printMembers(system1)
  // print role of all nodes
  println("@Message Node Roles \n" +
    Seq(system1, system2, system3).map(Cluster(_).selfMember)
      .map(member => s"  [${member.address}] ${member.roles.mkString(",")}")
      .mkString("\n"))

  // send Dot to system2
  println("@Action system2 ! Dot")
  system2 ! DotterGuardian.Dot
  println("@Action system2 ! Dot")
  system2 ! DotterGuardian.Dot

  implicit val timeout: Timeout = 5.seconds
  implicit val ec: ExecutionContext = system1.executionContext
  implicit val scheduler = system1.scheduler

  system2 ? (ref => DotterGuardian.Count(ref)) onComplete {
    case Success(count) => println(s"@Message system2.Counter.count = $count")
    case Failure(ex) => throw ex
  }

  // send Dot to system1, this message will not be handle
  system3 ! DotterGuardian.Dot

}
