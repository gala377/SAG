package sag

import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.receptionist.Receptionist
import akka.cluster.typed.Cluster

import com.typesafe.config.ConfigFactory
import scala.concurrent.duration.{FiniteDuration, MILLISECONDS}
import scala.collection.immutable.Set
import java.util.concurrent.TimeUnit

import warehouse._

object App {

  object Root {
    def apply(): Behavior[Nothing] = Behaviors.setup[Nothing] { ctx =>
        val warehouse = ctx.spawnAnonymous(Warehouse())
        val joiner = ctx.spawnAnonymous(Joiner(warehouse))
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
