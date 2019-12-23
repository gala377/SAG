package sag

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.{FiniteDuration}

import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors

import warehouse.Warehouse


object App {
  object Root {
    def apply(): Behavior[Nothing] = Behaviors.setup[Nothing] { ctx =>
        val warehouse = ctx.spawnAnonymous(Warehouse())
        val recorder = ctx.spawnAnonymous(Recorder())
        val joiner = ctx.spawnAnonymous(Joiner(warehouse, recorder))
        val collector = ctx.spawnAnonymous(Collector(
            joiner,
            FiniteDuration(2, TimeUnit.SECONDS)
        ))
        Behaviors.ignore
    }
  }

  def main(args: Array[String]): Unit = {
    val system = ActorSystem[Nothing](Root(), "Sag")
  }
}
