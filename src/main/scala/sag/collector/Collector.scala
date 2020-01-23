package sag.collector

import scala.concurrent.duration.FiniteDuration
import akka.actor.typed.scaladsl.{Behaviors, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior}
import sag.joiner
import sag.joiner.Joiner
import sag.types._

object Collector {

    sealed trait Command
    final case class DownloadNext() extends Command with CborSerializable
    final case class CartIsReady(cart: Cart) extends Command with CborSerializable
    final case class StartCaching() extends Command with CborSerializable
    final case class StartSending(sentTo: ActorRef[Joiner.Message]) extends Command with CborSerializable

    case object TimerKey

    def apply(sendTo: ActorRef[Joiner.Message], timeout: FiniteDuration): Behavior[Command] =
        Behaviors.setup { ctx =>
            Behaviors.withTimers { timer =>
                ctx.self ! DownloadNext()
                new Collector(timer, timeout).collectAndSend(sendTo)
            }
        }
}

private class Collector(timer: TimerScheduler[Collector.Command], timeout: FiniteDuration) {
    import Collector._

    def collectAndSend(joinerRef: ActorRef[joiner.Joiner.Message]): Behavior[Command] =
        Behaviors.receive { (ctx, message) =>
            message match {
                case StartCaching() =>
                    ctx.log.info(s"Caching messages started")
                    collectAndCache(Set())
                case StartSending(sentTo) =>
                    collectAndSend(sentTo)
                case DownloadNext() =>
                    ctx.spawnAnonymous(CartFetcher(ctx.self))
                    collectAndSend(joinerRef)
                case CartIsReady(cart) =>
                    ctx.log.info(s"Sending cart $cart to joiner")
                    joinerRef ! Joiner.Data(cart)
                    timer.startSingleTimer(TimerKey, DownloadNext(), timeout)
                    collectAndSend(joinerRef)
            }
        }

    def collectAndCache(msgSet: Set[Cart]): Behavior[Command] =
        Behaviors.receive { (ctx, message) =>
            message match {
                case StartCaching() =>
                    Behaviors.same
                case StartSending(sentTo) =>
                    msgSet.foreach(cart => {
                        ctx.log.info(s"Sending cached cart $cart")
                        sentTo ! Joiner.Data(cart)
                    })
                    collectAndSend(sentTo)
                case DownloadNext() =>
                    ctx.spawnAnonymous(CartFetcher(ctx.self))
                    collectAndCache(msgSet)
                case CartIsReady(cart) =>
                    ctx.log.info(s"Adding cart $cart to cache")
                    timer.startSingleTimer(TimerKey, DownloadNext(), timeout)
                    collectAndCache(msgSet + cart)
            }
        }
}

