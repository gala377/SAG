package sag.recorder

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import sag.data.{CborSerializable, JoinedCart}


object Recorder {

    final case class Data(content: JoinedCart) extends CborSerializable

    def apply(): Behavior[Data] = logMessages()

    def logMessages(): Behavior[Data] = Behaviors.receive {
        // TODO: timeout on joiner so that we can print
        // TODO: an error message
        (ctx, message) =>
            ctx.log.info(s"Got cart:\n${cartRepr(message.content)}")
            Behaviors.same
    }

    def cartRepr(cart: JoinedCart): String = {
        var repr = s"\tCart's id: ${cart.id}\n"
        for (p <- cart.ps) {
            repr += s"\t\tProduct[${p.id}]: ${p.description}\n"
        }
        repr
    }
}