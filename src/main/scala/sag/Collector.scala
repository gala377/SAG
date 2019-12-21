package sag

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


object Collector {
    sealed trait Command
    final case class DownloadNext(sentTo: ActorRef[Message]) extends Command with CBorSerializable

    sealed trait Message
    final case class Data(content: Cart) extends Message

    case object TimerKey

    def apply(sendTo: ActorRef[Message], timeout: FiniteDuration): Behavior[Command] = 
      Behaviors.setup { ctx =>
          Behaviors.withTimers { timer =>
            ctx.self ! DownloadNext(sendTo)
            new Collector(timer, timeout).collect(0)
          }
    }

    val maxNumOfProducts = 10
}

class Collector(timer: TimerScheduler[Collector.Command], timeout: FiniteDuration) {
    import Collector._

    def collect(id: Int): Behavior[Command] = Behaviors.receive{
        case (ctx, DownloadNext(sendTo)) =>
          val new_id = id + 1
          val cart = randomCart(id)
          sendTo ! Data(cart)
          timer.startSingleTimer(TimerKey, DownloadNext(sendTo), timeout)
          collect(new_id)
    }
    
    def randomCart(id: Int): Cart = {
        @tailrec
        def addProducts(cart: Cart, productsNum: Int): Cart = 
          if (productsNum == 0) 
              cart 
          else 
              addProducts(Cart(cart.id, cart.pids :+ randomProduct), productsNum-1)
        
        val numOfProducts = Random.nextInt(maxNumOfProducts)
        addProducts(Cart(id, Seq()), numOfProducts)
    }

    def randomProduct: Product =
        Products.products(Random.nextInt(Products.products.length)
}

