package sag

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

import data.{JoinedCart, CborSerializable}


object Recorder {

    final case class Data(content: JoinedCart) extends CborSerializable

    def apply(): Behavior[Data] = Behaviors.receive {
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