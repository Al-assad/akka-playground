package com.github.al.assad.akka.cluster.a_base

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.cluster.typed.Cluster
import akka.util.Timeout
import com.typesafe.config.ConfigFactory

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
          case Some(ref) => ref ! cmd; Behaviors.same
          case None => throw new IllegalStateException(s"ClusterListenerGuardian Actor not exist on {${selfMember.address}}")
        }
        case cmd: DotterGuardian.Command => dotter match {
          case Some(ref) => ref ! cmd; Behaviors.same
          case None => throw new IllegalStateException(s"DotterGuardian Actor not exist on {${selfMember.address}}")
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
           |akka.cluster.role = [${roles.mkString(",")}]
           |""".stripMargin)
      .withFallback(ConfigFactory.load("cluster-base"))

    ActorSystem[RuleRootActor.Command](RuleRootActor(), "ClusterSystem", config)
  }
}


/**
 * Testing App
 */
object ClusterNodeRuleTest1 extends App with ClusterNodeRuleTest {

  val system1 = launch(2551, Array("listener"))

}
