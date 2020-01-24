package sag.actors

import com.typesafe.config.Config

import akka.actor.typed.Behavior
import akka.actor.typed.receptionist.Receptionist

trait Guardian {
  def apply(args: Array[String], config: Config): Behavior[Receptionist.Listing]
}
