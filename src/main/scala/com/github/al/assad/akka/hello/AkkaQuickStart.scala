package com.github.al.assad.akka.hello

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import com.github.al.assad.akka.hello.GreeterMain.SayHello

/**
 * 官方 quick-start 示例
 * https://developer.lightbend.com/guides/akka-quickstart-scala/index.html
 */

/*
                         ┌───────spawn──────────────────┐
                         │                              ▼
                         │                ┌─────► Greater Actor ──────┐
                         │                │                           │
                         │                │                           │
                         │                │                           ▼
Greeter-Main Actor ──────┤            ┌► Greet                     Greeted
        ▲       │        │            │   ▲                           │
        │       └────────┼──reply─────┘   │                           │
        │                │                │                           │
        │                │                └──x── Greeter-Bot Actor ◄──┘
     SayHello            │                                ▲
                         │     before count < threshold   │
                         │                                │
                         └─────────spawn──────────────────┘
 */


// Greeter Actor
// Receive message Greet, then reply to message Greeted
object Greeter {

  final case class Greet(whom: String, replyTo: ActorRef[Greeted])
  final case class Greeted(whom: String, from: ActorRef[Greet])

  def apply(): Behavior[Greet] =
    Behaviors.receive { (context: ActorContext[Greet], message: Greet) =>
      // consume message Greet
      context.log.info("Hello {}!", message.whom)
      // send message Greeted
      message.replyTo ! Greeted(message.whom, context.self) // or: message.replyTo.tell(Greeted(message.whom, context.self))
      // passing behavioral state to downstream
      Behaviors.same
    }
}

// Greeter-Bot Actor
// Receive message Greeted, log the statistics of number of calls, then reply the message Greet.
// When the number of calls reaches the threshold, the behavior of Greeter-Bot actor will stop.
object GreeterBot {

  def apply(max: Int): Behavior[Greeter.Greeted] = {
    bot(0, max)
  }

  private def bot(greetingCounter: Int, max: Int): Behavior[Greeter.Greeted] =
    Behaviors.receive { (context, message) =>
      val n = greetingCounter + 1
      context.log.info("Greeting {} for {}", n, message.whom)
      if (n == max) {
        // stop behavior
        Behaviors.stopped
      } else {
        // reply to message Greet
        message.from ! Greeter.Greet(message.whom, context.self)
        bot(n, max)
      }
    }
}

// Greeter-Main Actor
// It's the guardian behavior of Greeter Actor and Greeter-Bot Actor.
// When the Greeter-Main Actor setups, spawn the Greeter Actor.
// When receive the message SayHello, spawn the Greeter-Bot Actor, then reply to the message Greet.
object GreeterMain {

  final case class SayHello(name: String)

  def apply(): Behavior[SayHello] =
    Behaviors.setup { context: ActorContext[SayHello] =>
      // when setup, spawn Greeter Actor
      val greeter: ActorRef[Greeter.Greet] = context.spawn(Greeter(), "greeter")
      Behaviors.receiveMessage { message: SayHello =>
        // when receive SayHello, spawn Greeter-Bot Actor
        val replyTo: ActorRef[Greeter.Greeted] = context.spawn(GreeterBot(max = 3), message.name)
        // reply to message Greet
        greeter ! Greeter.Greet(message.name, replyTo)
        Behaviors.same
      }
    }
}


// Main Class
object AkkaQuickStart extends App {
  // create ActorSystem with GreeterMain Actor as the guardian behavior.
  val greeterMain: ActorSystem[GreeterMain.SayHello] = ActorSystem(GreeterMain(), "AkkaQuickStart")
  // send message SayHello
  greeterMain ! SayHello("Charles")
}
