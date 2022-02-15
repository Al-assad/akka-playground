package com.github.al.assad.akka.cluster.a_base

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.cluster.ClusterEvent._
import akka.cluster.typed.{Cluster, Subscribe}
import com.typesafe.config.ConfigFactory

/**
 * Monitoring Cluster Events
 *
 * https://doc.akka.io/docs/akka/current/typed/cluster.html#
 */
object ClusterListener extends {

  sealed trait Event
  private final case class ReachabilityChange(event: ReachabilityEvent) extends Event
  private final case class MemberChange(event: MemberEvent) extends Event

  def apply(): Behavior[Event] = Behaviors.setup { ctx =>
    val log = ctx.log
    val reachabilityEventAdapter: ActorRef[ReachabilityEvent] = ctx.messageAdapter(ReachabilityChange.apply)
    val memberEventAdapter: ActorRef[MemberEvent] = ctx.messageAdapter(MemberChange.apply)

    Cluster(ctx.system).subscriptions ! Subscribe(reachabilityEventAdapter, classOf[ReachabilityEvent])
    Cluster(ctx.system).subscriptions ! Subscribe(memberEventAdapter, classOf[MemberEvent])

    Behaviors.receiveMessage[Event] { msg =>
      msg match {
        case ReachabilityChange(event) => event match {
          case UnreachableMember(member) => log.info("@Event => Member detected as unreachable: {}", member)
          case ReachableMember(member) => log.info("@Event => Member back to reachable: {}", member)
        }
        case MemberChange(event) => event match {
          case MemberUp(member) => log.info("@Event => Member is Up: {}", member.address)
          case MemberExited(member) => log.info("@Event => Member is Exiting: {}", member.address)
          case MemberRemoved(member, previousStatus) => log.info("@Event => Member is Removed: {} after {}", member.address, previousStatus)
          case MemberDowned(member) => log.info("@Event => Member is Down: {}", member.address)
          case MemberJoined(member) => log.info("@Event => Member is Joining: {}", member.address)
          case MemberLeft(member) => log.info("@Event => Member is Leaving: {}", member.address)
          case _ =>
        }
      }
      Behaviors.same
    }
  }
}


/**
 * Testing APP
 */
//noinspection DuplicatedCode
object TestClusterListener {

  object RootBehavior {
    def apply(): Behavior[Nothing] = Behaviors.setup[Nothing] { ctx =>
      ctx.spawn(ClusterListener(), "ClusterListener")
      Behaviors.empty
    }
  }

  def main(args: Array[String]): Unit = {
    val ports = if (args.isEmpty) Seq(25251, 25252, 0) else args.toSeq.map(_.toInt)

    ports.foreach { port =>
      val config = ConfigFactory
        .parseString(s"""akka.remote.artery.canonical.port=$port""".stripMargin)
        .withFallback(ConfigFactory.load("cluster-base"))

      ActorSystem[Nothing](RootBehavior(), "ClusterSystem", config)
    }
  }
}



