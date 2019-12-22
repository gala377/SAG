package sag

import scala.collection.immutable.{
    Map,
    Seq,
}
import scala.concurrent.duration.FiniteDuration

import akka.actor.typed.{
  ActorRef,
  Behavior,
}
import akka.actor.typed.scaladsl.{
  Behaviors, 
  TimerScheduler,
  ActorContext,
}

import data._
import warehouse._

object Joiner {
    // Should be a Set[Cart] but to do so
    // `Cart` needs to be compared based on
    // the `id` field. Right now it is just
    // a simple case class
    type Carts = Map[Cart.Id, Cart]

    sealed trait Message
    final case class Data(content: Cart) extends Message with CborSerializable
    final case class Products(id: Cart.Id, ps: Seq[Product.Id]) extends Message with CborSerializable
    
    final case class ListCarts(sendTo: ActorRef[ListCartsResponse]) extends Message with CborSerializable
    final case class ListCartsResponse(content: Carts) extends Message with CborSerializable

    def apply(warehouse: ActorRef[Warehouse.Message]): Behavior[Message] = 
        new Joiner(warehouse).listen(Map())
}

class Joiner(warehouse: ActorRef[Warehouse.Message]) {
    import Joiner._

    def listen(pending: Carts): Behavior[Message] =
        Behaviors.receive { (ctx, message) => message match {
            case Data(cart) => {
                ctx.log.info(s"Got new cart $cart")
                Behaviors.same
            }
            case ListCarts(sendTo) => {
                sendCarts(sendTo, pending)
                Behaviors.same
            }
            case _ => {
                Behaviors.same
            }
        }
    }

    def queueCart(ctx: ActorContext[Message], cart: Cart): Unit  = {
        // TODO
        // val self = ctx.messageAdapter[Warehouse.Receipt]
    }

    def sendCarts(
        to: ActorRef[ListCartsResponse],
        pending: Carts
    ): Unit = to ! ListCartsResponse(pending)

}