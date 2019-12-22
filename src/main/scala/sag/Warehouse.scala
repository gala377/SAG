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

  object Warehouse {

      sealed trait Message
      final case class Order(id: Int, ps: Seq[Product.Id], sendTo: ActorRef[Receipt])
      final case class Receipt(id: Int, ps: Seq[Product])

      def apply(): Behavior[Message] = 
        Behaviors.receive { (ctx, message) =>
            // TODO: Spawn child for each of the 
            // products ids, keep track of the orders
            // and so on. If this is te last one in order
            // then just send it back as a receipt
            Behaviors.ignore
        }
  }