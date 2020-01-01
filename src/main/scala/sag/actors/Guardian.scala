package sag.actors

import akka.actor.typed.Behavior
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.scaladsl.Behaviors

trait Guardian {
  def apply(args: Array[String]): Behavior[Receptionist.Listing]
}
