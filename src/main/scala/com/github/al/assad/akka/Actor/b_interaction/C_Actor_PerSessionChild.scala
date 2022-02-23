package com.github.al.assad.akka.Actor.b_interaction

import akka.NotUsed
import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import org.scalatest.wordspec.AnyWordSpecLike

/**
 * Per session child Actor sample.
 * https://doc.akka.io/docs/akka/current/typed/interaction-patterns.html#per-session-child-actor
 *
 * In some cases a complete response to a request can only be created and sent back after collecting
 * multiple answers from other actors. For these kinds of interaction it can be good to delegate the
 * work to a per “session” child actor. The child could also contain arbitrary logic to implement retrying,
 * failing on timeout, tail chopping, progress inspection etc.
 *
 * The following example simulates a federated query of User, Order data on different type of storage.
 */
object ActorPerSessionChild {

  case class User(id: Int, name: String)
  case class Order(id: Int, userId: Int, book: String)

  case class UserOrder(userId: String, userName: String, orders: Seq[Order])

  object UserDB {
    private val users = Map(
      1 -> User(1, "John"),
      2 -> User(2, "Mary"),
      3 -> User(3, "Mike")
    )
    sealed trait Command
    final case class GetUser(id: Int, replyTo: ActorRef[User]) extends Command
    def apply(): Behavior[Command] = Behaviors.receiveMessage {
      case GetUser(id, replyTo) =>
        replyTo ! users.get(id).orNull
        Behaviors.same
    }
  }

  object OrderDB {
    private val orders = Map(
      1 -> Order(11, 1, "Akka in Action"),
      2 -> Order(12, 1, "Learning Scala"),
      3 -> Order(13, 2, "Learning Akka"),
      4 -> Order(14, 3, "Learning to fly")
    )
    sealed trait Command
    final case class GetOrders(orderId: Int, replyTo: ActorRef[Seq[Order]]) extends Command
    def apply(): Behavior[Command] = Behaviors.receiveMessage {
      case GetOrders(id, replyTo) =>
        replyTo ! orders.values.filter(_.userId == id).toSeq
        Behaviors.same
    }
  }

  object DBQueryBox {
    sealed trait Command
    final case class GetUserOrder(userId: Int, replyTo: ActorRef[Option[UserOrder]]) extends Command

    def apply(): Behavior[Command] = Behaviors.setup { ctx =>
      val userStorage = ctx.spawn(UserDB(), "user-storage")
      val cityStorage = ctx.spawn(OrderDB(), "order-storage")

      Behaviors.receiveMessage[Command] {
        case GetUserOrder(userId, replyTo) =>
          ctx.spawn(queryUserOrders(userId, replyTo, userStorage, cityStorage), s"query-user-orders-$userId")
          Behaviors.same
      }
    }

    def queryUserOrders(userId: Int,
                        replyTo: ActorRef[Option[UserOrder]],
                        userStorage: ActorRef[UserDB.Command],
                        cityStorage: ActorRef[OrderDB.Command]): Behavior[NotUsed] =
      Behaviors.setup[AnyRef] { ctx =>
        var user: Option[User] = None
        var orders: Option[Seq[Order]] = None

        // narrow the ActorRef type to any subtype of the actual type we accept
        userStorage ! UserDB.GetUser(userId, ctx.self.narrow[User])
        cityStorage ! OrderDB.GetOrders(userId, ctx.self.narrow[Seq[Order]])

        // session receive behavior: merge Order and User to UserOrder
        def nextBehavior(): Behavior[AnyRef] = (user, orders) match {
          case (Some(u), Some(orders)) =>
            replyTo ! Some(UserOrder(u.id.toString, u.name, orders))
            Behaviors.same
          case (None, _) =>
            replyTo ! None
            Behaviors.same
          case _ =>
            Behaviors.same
        }

        // receive single actor message of session
        Behaviors.receiveMessage {
          case u: User =>
            user = Some(u)
            nextBehavior()
          case o: Seq[Order] =>
            orders = Some(o)
            nextBehavior()
        }
      }.narrow[NotUsed]
  }

}


class ActorPerSessionChildSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {
  import ActorPerSessionChild._

  "DBQueryBox" should {
    "query GetUserCity" in {
      val db = spawn(DBQueryBox(), "db-query-box")

      val probe = TestProbe[Option[UserOrder]]
      db ! DBQueryBox.GetUserOrder(1, probe.ref)
      println(probe.receiveMessage())

      val probe2 = TestProbe[Option[UserOrder]]
      db ! DBQueryBox.GetUserOrder(5, probe2.ref)
      println(probe2.receiveMessage())
    }
  }
}
