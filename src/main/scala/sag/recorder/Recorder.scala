package sag.recorder

import java.util.concurrent.TimeUnit

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{Behaviors, TimerScheduler}
import sag.payload.{CborSerializable, JoinedCart}

import scala.concurrent.duration.FiniteDuration

object Recorder {
    val RECORDER_TIMEOUT_IN_SEC = 30
    val timeout = new FiniteDuration(RECORDER_TIMEOUT_IN_SEC, TimeUnit.SECONDS)

    case object TimerKey

    sealed trait Message

    final case class Data(content: JoinedCart) extends Message with CborSerializable

    final case class TimeoutMsg() extends Message with CborSerializable

    def apply(): Behavior[Message] = {
        Behaviors.withTimers { timers =>
            timers.startSingleTimer(TimerKey, TimeoutMsg(), timeout)
            logMessages(timers)
        }
    }

    def logMessages(timer: TimerScheduler[Message]): Behavior[Message] = Behaviors.receive {
        (ctx, message) =>
            message match {
                case Data(cart) => {
                    ctx.log.info(s"Got cart:\n${cartRepr(cart)}")
                    timer.startSingleTimer(TimerKey, TimeoutMsg(), timeout)
                    Behaviors.same
                }
                case TimeoutMsg() => {
                    ctx.log.warn("RECORDER TIMEOUT!")
                    Behaviors.same
                }
            }
    }

    def cartRepr(cart: JoinedCart): String = {
        var repr = s"\tCart's id: ${cart.id}\n"
        for (p <- cart.products) {
            repr += s"\t\tProduct[${p.stockCode}]: ${p.description}\n"
        }
        repr
    }
}