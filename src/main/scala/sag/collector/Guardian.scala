package sag.collector

import java.util.concurrent.TimeUnit

import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior, receptionist}
import com.typesafe.config.Config
import sag.collector.Collector.{StartCaching, StartSending}
import sag.{actors, joiner}

import scala.concurrent.duration.FiniteDuration

object Guardian extends actors.Guardian {
    val ServiceKey: receptionist.ServiceKey[Collector.Command] = receptionist.ServiceKey[Collector.Command]("Collector")

    def apply(args: Array[String], config: Config): Behavior[Receptionist.Listing] =
        Behaviors.setup { ctx =>
            ctx.system.receptionist ! Receptionist.Subscribe(joiner.Guardian.ServiceKey, ctx.self)
            new Guardian(
                config.getLong("sag.collector.timeout")
            ).spawnCollector()
        }
}

private class Guardian(collectorTimeout: Long) {

    import Guardian._

    def spawnCollector(): Behavior[Receptionist.Listing] = {
        Behaviors.receive { (ctx, message) =>
            message match {
                case joiner.Guardian.ServiceKey.Listing(listings) =>
                    if (listings.isEmpty) {
                        ctx.log.info(s"Waiting for joiner")
                        Behaviors.same
                    }
                    else {
                        ctx.log.info(s"Spawning collector")
                        val joiner = listings.toIndexedSeq(0)
                        val collector = ctx.spawnAnonymous(
                            Collector(joiner, new FiniteDuration(collectorTimeout, TimeUnit.SECONDS)))
                        ctx.system.receptionist ! Receptionist.Register(ServiceKey, collector)
                        checkState(joiner, collector)
                    }
            }
        }
    }

    def checkState(
      joinerRef: ActorRef[joiner.Joiner.Message],
      collectorRef: ActorRef[Collector.Command]
    ): Behavior[Receptionist.Listing] = {
        Behaviors.receive { (ctx, message) =>
            message match {
                case joiner.Guardian.ServiceKey.Listing(listings) =>
                    if (listings(joinerRef)) {
                        Behaviors.same
                    }
                    else if (listings.isEmpty) {
                        ctx.log.info(s"Oops joiner is dead")
                        collectorRef ! StartCaching()
                        waitForJoiner(collectorRef)
                    }
                    else {
                        ctx.log.info(s"Sending other joiner reference")
                        val newJoiner = listings.toIndexedSeq(0)
                        collectorRef ! StartSending(newJoiner)
                        checkState(newJoiner, collectorRef)
                    }
            }
        }
    }

    def waitForJoiner(
      collectorRef: ActorRef[Collector.Command]
    ): Behavior[Receptionist.Listing] = {
        Behaviors.receive { (ctx, message) =>
            message match {
                case joiner.Guardian.ServiceKey.Listing(listings) if listings.nonEmpty => {
                    val joiner = listings.toIndexedSeq(0)
                    ctx.log.info(s"Sending new joiner reference")
                    collectorRef ! StartSending(joiner)
                    checkState(joiner, collectorRef)
                }
                case _ => Behaviors.same
            }
        }
    }
}
