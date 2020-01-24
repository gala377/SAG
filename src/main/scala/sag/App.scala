package sag

import scala.collection.immutable.Map

import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.typed.Cluster

import com.typesafe.config.{ConfigFactory, Config}

object App {
  
  val Roles: Map[String, actors.Guardian] = Map(
    ("collector", collector.Guardian),
    ("joiner", joiner.Guardian),
    ("warehouse", warehouse.Guardian),
    ("recorder", recorder.Guardian),
  )

  object Root {
    def apply(args: Array[String], config: Config): Behavior[Nothing] = Behaviors.setup[Nothing] { ctx =>
        ctx.log.info(s"Starting actor system.")
        val cluster = Cluster(ctx.system)
        for((role, actor) <- Roles) {
          if(cluster.selfMember.hasRole(role)) {
            ctx.log.info(s"System role is $role")
            ctx.spawnAnonymous(actor(args, config))
          }
        }
        Behaviors.empty
    }
  }

  def main(args: Array[String]): Unit = {
    if (args.length < 2) {
      throw new RuntimeException("Need a role and a port")
    }

    val role = args(0)
    if (!Roles.contains(role)) {
      throw new RuntimeException(s"Unknown role $role")
    }

    start(role, args(1).toInt, args.drop(2))
  }

  def start(role: String, port: Int, args: Array[String]): Unit = {
    val config = ConfigFactory.parseString(s"""
      akka.remote.artery.canonical.port=$port
      akka.cluster.roles = [$role]
    """).withFallback(ConfigFactory.load("application"))

    val system = ActorSystem[Nothing](Root(args, config), "Sag", config)
  }
}
