package sag.recorder

import java.util.concurrent.TimeUnit

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.receptionist
import akka.actor.typed.receptionist.Receptionist
import com.typesafe.config.Config
import sag.actors

import scala.concurrent.duration.FiniteDuration

object Guardian extends actors.Guardian {

    val ServiceKey: receptionist.ServiceKey[Recorder.Data] =
      receptionist.ServiceKey("Recorder")

    def apply(args: Array[String], config: Config): Behavior[Receptionist.Listing] =
        Behaviors.setup{ ctx =>
          val recorder = ctx.spawnAnonymous(Recorder(
            new FiniteDuration(config.getLong("sag.recorder.timeout"), TimeUnit.SECONDS)))
          ctx.system.receptionist ! Receptionist.Register(ServiceKey, recorder)
          Behaviors.ignore
        }
}