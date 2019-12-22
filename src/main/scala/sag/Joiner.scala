package sag

import scala.collection.mutable.Map
import scala.collection.immutable.Seq
import scala.annotation.tailrec
import scala.concurrent.duration.FiniteDuration
import scala.util.Random

import akka.actor.typed.{
  ActorRef,
  Behavior,
}
import akka.actor.typed.scaladsl.{
  Behaviors, 
  TimerScheduler,
}

import data._

object Joiner {

    sealed trait Message
    final case class Data(content: Cart) extends Message with CborSerializable
    final case class Products(id: Cart.Id, ps: Seq[Product.Id]) extends Message with CborSerializable

    def apply(warehouse: ActorRef[Warehouse.Message]): Behavior[Message] = 
        Behaviors.receive { (ctx, message) => message match {
            case Data(cart) => {
                ctx.log.info(s"Got new cart $cart")
                Behaviors.same
            }
            case _ => {
                Behaviors.same
            }
        }

    }
}

// class Joiner(pending: Map[Cart.Id, Cart]) {



// }