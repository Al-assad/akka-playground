package com.github.al.assad.akka.actor.c_feature

import akka.Done
import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, StashBuffer}
import akka.actor.typed.{ActorRef, Behavior}
import com.github.al.assad.akka.TestImplicit.TestProbeWrapper
import com.github.al.assad.akka.sleep
import org.scalatest.wordspec.AnyWordSpecLike

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.{Failure, Success}

/**
 * Actor stash sample.
 *
 * https://doc.akka.io/docs/akka/current/typed/stash.html
 *
 * Stashing enables an actor to temporarily buffer all or some messages
 * that cannot or should not be handled using the actor’s current behavior.
 */
//noinspection DuplicatedCode
object ActorStash {

  trait DB {
    def init: Future[Done]

    def get(id: Int): Future[String]
  }

  class DelayDB extends DB {
    private val data = mutable.Map.empty[Int, String]
    override def init: Future[Done] = Future {
      sleep(2.seconds)
      data ++= Map(1 -> "one", 2 -> "two", 3 -> "three")
      Done
    }
    override def get(id: Int): Future[String] = Future.successful(data.get(id).orNull)
  }

  /**
   * Not using stash sample.
   */
  object UnStashDataAccess {
    sealed trait Command
    case class Get(id: Int, replyTo: ActorRef[Option[String]]) extends Command

    private case object DBInitDone extends Command
    private case class DBError(cause: Throwable) extends Command
    private case class WrappedDBGet(value: String, replyTo: ActorRef[Option[String]]) extends Command

    def apply(db: DB): Behavior[Command] = Behaviors.setup { ctx =>
      // init db
      ctx.pipeToSelf(db.init) {
        case Success(value) => value match {
          case Done => DBInitDone
          case state => DBError(new RuntimeException(s"DB init failed, $state"))
        }
        case Failure(cause) => DBError(cause)
      }
      // receive command
      active(db)
    }

    private def active(db: DB): Behavior[Command] = Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case Get(id, replyTo) =>
          ctx.pipeToSelf(db.get(id)) {
            case Success(value) => WrappedDBGet(value, replyTo)
            case Failure(ex) => DBError(ex)
          }
          Behaviors.same
        case WrappedDBGet(value, replyTo) =>
          replyTo ! Option(value)
          Behaviors.same
        case DBError(cause) =>
          ctx.log.error("DB error", cause)
          println(s"DB error: $cause")
          Behaviors.same
        case DBInitDone =>
          ctx.log.info("DB init done")
          println("DB init done")
          Behaviors.same
      }
    }
  }

  /**
   * using stash sample.
   */

  object StashDataAccess {
    sealed trait Command
    case class Get(id: Int, replyTo: ActorRef[Option[String]]) extends Command

    private case object DBInitDone extends Command
    private case class DBError(cause: Throwable) extends Command
    private case class WrappedDBGet(value: String, replyTo: ActorRef[Option[String]]) extends Command

    def apply(db: DB): Behavior[Command] =
      Behaviors.withStash(100) { buffer =>
        Behaviors.setup { ctx =>
          start(ctx, buffer, db)
        }
      }

    // start actor: stash and unstash behavior
    private def start(ctx: ActorContext[Command], buffer: StashBuffer[Command], db: DB): Behavior[Command] = {
      // init db
      ctx.pipeToSelf(db.init) {
        case Success(value) => value match {
          case Done => DBInitDone
          case state => DBError(new RuntimeException(s"DB init failed, $state"))
        }
        case Failure(cause) => DBError(cause)
      }
      Behaviors.receiveMessage {
        case DBInitDone =>
          // unstash message
          buffer.unstashAll(active(db))
        case DBError(cause) =>
          throw cause
        case other =>
          // stash message
          buffer.stash(other)
          Behaviors.same
      }
    }

    private def active(db: DB): Behavior[Command] = Behaviors.receive[Command] { (ctx, msg) =>
      msg match {
        case Get(id, replyTo) =>
          ctx.pipeToSelf(db.get(id)) {
            case Success(value) => WrappedDBGet(value, replyTo)
            case Failure(ex) =>
              ctx.log.error("DB error", ex)
              WrappedDBGet(null, replyTo)
          }
          Behaviors.same
        case WrappedDBGet(value, replyTo) =>
          replyTo ! Option(value)
          Behaviors.same
      }
    }
  }


}


class ActorStashSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  import ActorStash._

  "Actor Stash" should {

    "UnStashDataAccess" in {
      import UnStashDataAccess._
      val db = spawn(UnStashDataAccess(new DelayDB))

      TestProbe[Option[String]] to { probe =>
        db ! Get(1, probe.ref)
        probe.expectMessage(None) // db is not yet initialized
      }

      TestProbe[Option[String]] to { probe =>
        sleep(3.seconds)
        db ! Get(1, probe.ref)
        probe.expectMessage(Some("one")) // db was initialized
      }
    }

    "StashDataAccess" in {
      import StashDataAccess._
      val db = spawn(StashDataAccess(new DelayDB))

      TestProbe[Option[String]] to { probe =>
        db ! Get(1, probe.ref)
        probe.expectMessage(Some("one"))
        db ! Get(2, probe.ref)
        probe.expectMessage(Some("two"))
      }
    }

    "StashDataAccess over stash" in {
      import StashDataAccess._
      val db = spawn(StashDataAccess(new DelayDB))

      TestProbe[Option[String]] to { probe =>
        (1 to 101) foreach (_ => db ! Get(1, probe.ref))
        // the stash is full and it will receive the rest of the messages
        db ! Get(2, probe.ref)
        probe.expectNoMessage()
      }
    }

  }
}
