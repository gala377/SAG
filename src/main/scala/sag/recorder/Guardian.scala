package sag.recorder

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.receptionist
import akka.actor.typed.receptionist.Receptionist

import sag.actors

object Guardian extends actors.Guardian {

    val ServiceKey: receptionist.ServiceKey[Recorder.Data] =
      receptionist.ServiceKey("Recorder")

    def apply(args: Array[String]): Behavior[Receptionist.Listing] =
        Behaviors.setup{ ctx =>
          val recorder = ctx.spawnAnonymous(Recorder())
          ctx.system.receptionist ! Receptionist.Register(ServiceKey, recorder)
          Behaviors.ignore
        }
}