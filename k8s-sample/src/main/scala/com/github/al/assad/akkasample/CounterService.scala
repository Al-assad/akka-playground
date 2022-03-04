package com.github.al.assad.akkasample

import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior, Scheduler, SupervisorStrategy}
import akka.cluster.typed.{ClusterSingleton, SingletonActor}
import akka.http.scaladsl.server.{Directives, Route}
import akka.util.Timeout

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

class CounterService(counter: ActorRef[_ >: CounterActor.Command])
                    (implicit scheduler: Scheduler, executionContext: ExecutionContext)
  extends Directives with DefaultJsonFormats {

  implicit val timeout: Timeout = 3.seconds

  /**
   * /counter/incre
   * /counter/incre/{:num}
   * /counter/value
   */
  val route = pathPrefix("counter") {
    increRoute ~
    getValueRoute
  }


  def increRoute: Route = pathPrefix("incre") {
    get {
      pathEndOrSingleSlash {
        counter ! CounterActor.Increment(1)
        complete("Incremented Counter by 1")
      } ~
      path(IntNumber) { n =>
        counter ! CounterActor.Increment(n)
        complete(s"Incremented Counter by $n")
      }
    }
  }

  def getValueRoute: Route = path("value") {
    get & complete {
      (counter ? CounterActor.GetValue).map(e => s"Counter Value $e")
    }
  }

}


/**
 * Counter Actor
 */
object CounterActor {
  sealed trait Command extends CounterActorProxy.Command
  final case class Increment(n: Int) extends Command
  final case class GetValue(replyTo: ActorRef[Int]) extends Command

  def apply(): Behavior[Command] = Behaviors.setup { ctx =>
    ctx.log.info("CounterActor created.")
    var value = 0
    Behaviors.receiveMessage {
      case Increment(n) =>
        value += n
        Behaviors.same
      case GetValue(replyTo) =>
        replyTo ! value
        Behaviors.same
    }
  }
}


/**
 * Cluster Singleton Counter Actor
 */
object CounterActorProxy {
  trait Command extends CborSerializable

  def apply(): Behavior[Command] = Behaviors.setup { ctx =>
    ctx.log.info("CounterActor singleton proxy created.")
    val singletonManager = ClusterSingleton(ctx.system)
    val counterProxy = singletonManager.init(
      SingletonActor(
        Behaviors.supervise(CounterActor()).onFailure[Exception](SupervisorStrategy.restart),
        "counter-singleton"))
    Behaviors.receiveMessage[Command] {
      case cmd: CounterActor.Command =>
        counterProxy ! cmd
        Behaviors.same
    }
  }
}


