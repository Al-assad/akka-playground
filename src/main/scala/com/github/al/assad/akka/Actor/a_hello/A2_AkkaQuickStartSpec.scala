package com.github.al.assad.akka.Actor.a_hello

import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import akka.actor.typed.ActorRef
import Greeter.{Greet, Greeted}
import com.github.al.assad.akka.Actor.a_hello.Greeter.{Greet, Greeted}
import org.scalatest.wordspec.AnyWordSpecLike

class A2_AkkaQuickStartSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  "A Greeter" must {
    "reply to greeted" in {
      // create a probe to track messages Greeted
      val replyProbe: TestProbe[Greeted] = createTestProbe[Greeted]()
      // spawn the Greet Action
      val underTest: ActorRef[Greet] = spawn(Greeter())
      // send a Greet message, using the replyProbe as reply context
      underTest ! Greet("Santa", replyProbe.ref)
      // expect the Greeted message
      replyProbe.expectMessage(Greeted("Santa", underTest.ref))
    }
  }

}
