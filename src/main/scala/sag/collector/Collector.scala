package sag.collector

import akka.actor.typed.scaladsl.{Behaviors, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior}
import sag.data._
import sag.joiner
import sag.joiner.Joiner

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
                new Collector(timer, timeout).collect(0, Option(sendTo))
            }
        }

    val maxNumOfProducts = 10
}

private class Collector(timer: TimerScheduler[Collector.Command], timeout: FiniteDuration) {

    import Collector._

    var msgSet: Set[Cart] = Set()

    def collect(id: Int, joinerRef: Option[ActorRef[joiner.Joiner.Message]]): Behavior[Command] = Behaviors.receive {
        case (ctx, StartCaching()) =>
            ctx.log.info(s"Caching messages started")
            collect(id, None)
        case (ctx, StartSending(sentTo)) =>
            //todo we will probably lose data if joiner dies when sending cached msg
            msgSet.foreach(cart => {
                ctx.log.info(s"Sending cached cart $cart")
                sentTo ! Joiner.Data(cart)
            })
            msgSet = Set()
            collect(id, Option(sentTo))
        case (ctx, DownloadNext()) =>
            val new_id = id + 1
            val cart = randomCart(id)
            if (joinerRef.isEmpty) {
                ctx.log.info(s"Caching cart $cart")
                msgSet += cart
            } else {
                ctx.log.info(s"Sending cart $cart")
                joinerRef.get ! Joiner.Data(cart)
            }
            timer.startSingleTimer(TimerKey, DownloadNext(), timeout)
            collect(new_id, joinerRef)
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

