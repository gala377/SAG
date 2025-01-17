package sag.recorder

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{Behaviors, TimerScheduler}
import sag.payload.{CborSerializable, JoinedCart}

import scala.concurrent.duration.FiniteDuration

object Recorder {

    case object TimerKey

    sealed trait Message
    final case class Data(content: JoinedCart) extends Message with CborSerializable
    final case class TimeoutMsg() extends Message with CborSerializable

    def apply(timeout: FiniteDuration): Behavior[Message] = {
        Behaviors.withTimers { timers =>
            timers.startSingleTimer(
                TimerKey,
                TimeoutMsg(),
                timeout)
            logMessages(timers, timeout)
        }
    }

    def logMessages(
      timer: TimerScheduler[Message],
      timeout: FiniteDuration
    ): Behavior[Message] = Behaviors.receive {
        (ctx, message) =>
            ctx.setLoggerName("sag.recorder.Recorder")
            message match {
                case Data(cart) =>
                    ctx.log.info(s"Got cart:\n${cartRepr(cart)}")
                    timer.startSingleTimer(TimerKey, TimeoutMsg(), timeout)
                    Behaviors.same
                case TimeoutMsg() =>
                    ctx.log.warn("Timed out on joiner")
                    Behaviors.same
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