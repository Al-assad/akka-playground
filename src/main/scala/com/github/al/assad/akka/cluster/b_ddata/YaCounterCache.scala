package com.github.al.assad.akka.cluster.b_ddata

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.ddata.{GCounter, GCounterKey}
import akka.cluster.ddata.typed.scaladsl.{DistributedData, Replicator}
import akka.util.Timeout

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

object YaCounterCache {

  sealed trait Command
  final case class Increment(value: Int = 1) extends Command
  final case class GetValue(replyTo: ActorRef[Int]) extends Command

  private sealed trait InternalCommand extends Command
  private final case class InternalUpdate(rsp: Replicator.UpdateResponse[GCounter]) extends InternalCommand
  private final case class InternalGetValue(rsp: Replicator.GetResponse[GCounter], replyTo: ActorRef[Int]) extends InternalCommand

  def apply(id: String): Behavior[Command] = Behaviors.setup { ctx =>
    implicit val node = DistributedData(ctx.system).selfUniqueAddress
    implicit val timeout: Timeout = 10.seconds
    val cacheKey = GCounterKey(id)

    DistributedData.withReplicatorMessageAdapter[Command, GCounter] { replicator =>
      Behaviors.receiveMessage {

        case Increment(value) =>
          replicator.askUpdate(
            Replicator.Update(cacheKey, GCounter.empty, Replicator.WriteMajority(5 seconds))(_ :+ value),
            InternalUpdate.apply)
          Behaviors.same

        case GetValue(replyTo) =>
          replicator.askGet(
            replyTo => Replicator.Get(cacheKey, Replicator.ReadLocal, replyTo),
            rsp => InternalGetValue(rsp, replyTo))
          Behaviors.same

        case InternalUpdate(_@Replicator.UpdateSuccess(_)) =>
          Behaviors.same

        case InternalGetValue(rsp@Replicator.GetSuccess(id), replyTo) =>
          replyTo ! rsp.get(id).value.toInt
          Behaviors.same

        case _ =>
          Behaviors.unhandled
      }
    }
  }

}
