package sag.collector

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.receptionist
import akka.actor.typed.receptionist.Receptionist

import sag.joiner
import sag.actors

object Guardian extends actors.Guardian {

    val ServiceKey = receptionist.ServiceKey[Collector.Command]("Collector")

    def apply(args: Array[String]): Behavior[Receptionist.Listing] = 
        Behaviors.setup { ctx =>
          // TODO: Not working
            ctx.system.receptionist ! Receptionist.Find(joiner.Gurdian.ServiceKey)
        }
}

private class Guardian {

}