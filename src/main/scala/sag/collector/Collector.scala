package sag.collector

import akka.actor.typed.scaladsl.{Behaviors, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior}
import sag.joiner
import sag.joiner.Joiner
import sag.types._

import scala.annotation.tailrec
import scala.concurrent.duration.FiniteDuration
import scala.util.Random

object Collector {

    sealed trait Command

    final case class DownloadNext() extends Command with CborSerializable

    final case class StartCaching() extends Command with CborSerializable

    final case class StartSending(sentTo: ActorRef[Joiner.Message]) extends Command with CborSerializable

    case object TimerKey

    def apply(sendTo: ActorRef[Joiner.Message], timeout: FiniteDuration): Behavior[Command] =
        Behaviors.setup { ctx =>
            Behaviors.withTimers { timer =>
                ctx.self ! DownloadNext()
                new Collector(timer, timeout).collectAndSend(0, sendTo)
            }
        }

    val maxNumOfProducts = 10
}

private class Collector(timer: TimerScheduler[Collector.Command], timeout: FiniteDuration) {

    import Collector._

    def collectAndSend(id: Int, joinerRef: ActorRef[joiner.Joiner.Message]): Behavior[Command] =
        Behaviors.receive { (ctx, message) =>
            message match {
                case StartCaching() =>
                    ctx.log.info(s"Caching messages started")
                    collectAndCache(id, Set())
                case StartSending(sentTo) =>
                    collectAndSend(id, sentTo)
                case DownloadNext() =>
                    val cart = randomCart(id)
                    ctx.log.info(s"Sending cart $cart")
                    joinerRef ! Joiner.Data(cart)
                    timer.startSingleTimer(TimerKey, DownloadNext(), timeout)
                    collectAndSend(id + 1, joinerRef)
            }
        }

    def collectAndCache(id: Int, msgSet: Set[Cart]): Behavior[Command] =
        Behaviors.receive { (ctx, message) =>
            message match {
                case StartCaching() =>
                    Behaviors.same
                case StartSending(sentTo) =>
                    msgSet.foreach(cart => {
                        ctx.log.info(s"Sending cached cart $cart")
                        sentTo ! Joiner.Data(cart)
                    })
                    collectAndSend(id, sentTo)
                case DownloadNext() =>
                    val cart = randomCart(id)
                    ctx.log.info(s"Caching cart $cart")
                    timer.startSingleTimer(TimerKey, DownloadNext(), timeout)
                    collectAndCache(id + 1, msgSet + cart)
            }
        }


    def randomCart(id: Int): Cart = {
        @tailrec
        def addProducts(cart: Cart, productsNum: Int): Cart =
            if (productsNum == 0)
                cart
            else
                addProducts(Cart(cart.id, cart.pids :+ randomProductId), productsNum - 1)

        val numOfProducts = Random.nextInt(maxNumOfProducts)
        addProducts(Cart(id, Seq()), numOfProducts)
    }

    def randomProductId: Product.Id =
        Random.nextInt(Products.products.length)
}

