package com.github.al.assad.akka.Cluster.a_base

import akka.actor.typed.pubsub.Topic
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior}
import com.github.al.assad.akka.Cluster.CborSerializable
import com.github.al.assad.akka.Cluster.a_base.ClusterActorTopicSubscribeTest.TopicManager
import com.github.al.assad.akka.sleep
import com.typesafe.config.ConfigFactory

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.DurationInt

/**
 * Akka Cluster Topics subscription and publishing sample
 *
 * Each topic is represented by one Receptionist service key meaning that the number of
 * topics will scale to thousands or tens of thousands but for higher numbers of topics
 * will require custom solutions. It also means that a very high turnaround of unique
 * topics will not work well and for such use cases a custom solution is advised.
 */
object ClusterActorTopicSubscribeTest {

  case class Message(msg: String) extends CborSerializable

  object TopicManager {
    sealed trait Command
    case class Push(msg: String) extends Command

    def apply(): Behavior[Command] = Behaviors.setup { ctx =>
      // spawn topic actor
      val topic = ctx.spawn(Topic[Message]("test-topic"), "Topic")
      val messageCounter = ctx.spawn(MessageCounter(), "MessageCounter")
      val messageCollector = ctx.spawn(MessageCollector(), "MessageCollector")
      // subscribe topic
      topic ! Topic.Subscribe(messageCounter)
      topic ! Topic.Subscribe(messageCollector)

      Behaviors.receiveMessage {
        case Push(msg) =>
          topic ! Topic.Publish(Message(msg))
          Behaviors.same
      }
    }
  }

  object MessageCounter {
    def apply(): Behavior[Message] = Behaviors.setup {ctx =>
      var count = 0
      Behaviors.receiveMessage {
        case Message(msg) =>
          count += 1
          ctx.log.info(s"@Message MessageCounter[${ctx.system.address}] message count: $count")
          Behaviors.same
      }
    }
  }

  object MessageCollector {
    def apply(): Behavior[Message] = Behaviors.setup { ctx =>
      val messages = ArrayBuffer.empty[String]
      Behaviors.receiveMessage {
        case Message(msg) =>
          messages += msg
          ctx.log.info(s"@Message MessageCollector[${ctx.system.address}] messages: $messages")
          Behaviors.same
      }
    }
  }

}



trait ClusterActorTopicSubscribeTest {
  def launch(port: Int): ActorSystem[TopicManager.Command] = {
    val config = ConfigFactory
      .parseString(s"""akka.remote.artery.canonical.port = $port""".stripMargin)
      .withFallback(ConfigFactory.load("cluster-base"))
    ActorSystem[TopicManager.Command](TopicManager(), "ClusterSystem", config)
  }
}


object ClusterActorTopicSubscribeTest1 extends App with ClusterActorTopicSubscribeTest {
  val (system1, system2) = (launch(25251), launch(25252))
  waitSystemUp(system1, system2)

  sleep(5.seconds)
  println("@Action system1 ! TopicManager.Push(Hello)")
  system1 ! TopicManager.Push("Hello")
  sleep(5.seconds)

  println("@Action system1 ! TopicManager.Push(World)")
  system1 ! TopicManager.Push("World")
  sleep(5.seconds)

  println("@Action system2 ! TopicManager.Push(Hi)")
  system2 ! TopicManager.Push("Hi")
}
