package com.github.al.assad.akka.Actor.b_interaction

import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.github.al.assad.akka.Actor.b_interaction.ActorSendFutureToSelf.DBAccess.{GetData, UpdateData}
import com.github.al.assad.akka.{rand, sleep}
import org.scalatest.wordspec.AnyWordSpecLike

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

/**
 * Actor send Future to self sample.
 */
object ActorSendFutureToSelf {

  case class User(id: Int, name: String)

  object DBAccess {
    sealed trait Command
    final case class UpdateData(user: User) extends Command
    final case class GetData(id: Int, replyTo: ActorRef[Option[User]]) extends Command

    sealed trait Internal extends Command
    final case class WrappedStorageGetRes(user: Option[User], replyTo: ActorRef[Option[User]]) extends Internal

    def apply(): Behavior[Command] = Behaviors.receive { (ctx, message) =>
      message match {
        case UpdateData(user) =>
          updateData(user)
          Behaviors.same

        case GetData(id, replyTo) =>
          val getResultFuture = getData(id)
          // use pipeToSelf to send the future result to actor self
          ctx.pipeToSelf(getResultFuture) {
            case Success(user) => WrappedStorageGetRes(user, replyTo)
            case Failure(ex) =>
              ctx.log.error("fail to get data from storage", ex)
              WrappedStorageGetRes(None, replyTo)
          }
          Behaviors.same

        case WrappedStorageGetRes(user, replyTo) =>
          replyTo ! user
          Behaviors.same
      }
    }

    val storage = mutable.Map.empty[Int, User]

    private def getData(id: Int): Future[Option[User]] = Future {
      // simulation of time-consuming tasks
      sleep(rand.nextInt(1500))
      storage.get(id)
    }

    private def updateData(user: User): Unit = Future {
      // simulation of time-consuming tasks
      sleep(rand.nextInt(2000))
      storage.put(user.id, user)
    }
  }
}

class ActorSendFutureToSelfSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  import ActorSendFutureToSelf._

  "DBAccess" should {
    "update and get data" in {
      val storage = spawn(DBAccess())
      storage ! UpdateData(User(1, "assad"))
      storage ! UpdateData(User(2, "alex"))

      sleep(2.seconds)
      val probe1 = TestProbe[Option[User]]()
      storage ! GetData(1, probe1.ref)
      probe1.expectMessage(Some(User(1, "assad")))

      val probe2 = TestProbe[Option[User]]()
      storage ! GetData(4, probe2.ref)
      probe2.expectMessage(None)
    }
  }
}
