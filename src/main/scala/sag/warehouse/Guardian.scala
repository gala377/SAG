package sag.warehouse

import akka.actor.typed.Behavior
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.receptionist
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.config.Config
import sag.actors

object Guardian extends actors.Guardian {

  val ServiceKey: receptionist.ServiceKey[Warehouse.Message] =
    receptionist.ServiceKey("Warehouse")

  def apply(args: Array[String], config: Config): Behavior[Receptionist.Listing] =
    Behaviors.setup{ ctx =>
      val warehouse = ctx.spawnAnonymous(Warehouse())
      ctx.system.receptionist ! Receptionist.Register(ServiceKey, warehouse)
      Behaviors.ignore
    }
}