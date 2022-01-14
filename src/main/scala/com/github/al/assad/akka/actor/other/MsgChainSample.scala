package com.github.al.assad.akka.actor.other

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}

import scala.concurrent.duration.DurationInt


/**
 * Actor 消息传递示例
 *
 * Supervisor
 * -> BotManager
 * ----> BotA
 * ----> BotB
 * -> PingManager
 * ----> BotC
 */
object MsgChainSample {

  object Supervisor {
    trait Command

    def apply(): Behavior[Command] = Behaviors.setup { context =>
      val botManager = context.spawn(BotManager(), "botManager")
      val pingManager = context.spawn(PingManager(), "pingManager")
      context.watch(botManager)
      context.watch(pingManager)
      Behaviors.receiveMessage {
        case msg: BotManager.Command =>
          botManager ! msg
          Behaviors.same
        case msg: PingManager.Request =>
          pingManager ! msg
          Behaviors.same
      }
    }

  }


  object BotManager {
    trait Command extends Supervisor.Command

    def apply(): Behavior[Command] = Behaviors.setup { context =>
      context.log.info("BotManager started")
      val botA = context.spawn(BotA(), "bot-a")
      val botB = context.spawn(BotB(), "bot-b")
      context.watch(botA)
      context.watch(botB)
      Behaviors.receiveMessage {
        case cmd: BotA.Assign =>
          botA ! cmd
          Behaviors.same
        case cmd: BotB.Assign =>
          botB ! cmd
          Behaviors.same
      }
    }
  }

  object BotA {
    case class Assign(info: String) extends BotManager.Command

    def apply(): Behavior[Assign] = Behaviors.receive { (context, message) =>
      context.log.info(s"BotA received message: $message")
      Behaviors.same
    }
  }

  object BotB {
    case class Assign(info: String) extends BotManager.Command

    def apply(): Behavior[Assign] = Behaviors.receive { (context, message) =>
      context.log.info(s"BotB received message: $message")
      Behaviors.same
    }
  }


  object PingManager {

    trait Request
    trait Response
    case class Ping(user: String) extends Supervisor.Command with Request

    implicit val timeout = 2.seconds
    def apply(): Behavior[Request] = Behaviors.setup { context =>
      context.log.info("PingManager started")
      val botC = context.spawn(BotC(), "bot-c")
      val botD = context.spawn(BotD(), "bot-d")
      Behaviors.receiveMessage {
        case Ping(user) =>
          botC ! BotC.Message(user, botD)
          Behaviors.same
      }
    }

  }

  object BotC {
    case class Message(user: String, replyTo: ActorRef[Result]) extends PingManager.Request
    case class Result(info: String) extends PingManager.Response

    def apply(): Behavior[Message] = Behaviors.receive {
      case (context, Message(user, replyTo)) =>
        context.log.info(s"BotC received message: $user")
        replyTo ! Result(s"hello, $user")
        Behaviors.same
    }
  }

  object BotD {
    def apply(): Behavior[PingManager.Response] = Behaviors.receive {
      case (context, BotC.Result(info)) =>
        context.log.info(s"BotD received message: $info")
        Behaviors.same
    }
  }

  def main(args: Array[String]): Unit = {
    val system = ActorSystem(Supervisor(), "system")
    system ! BotA.Assign("work!")
    system ! BotB.Assign("hello")
    system ! PingManager.Ping("al-assad")


  }

}
