package sag.collector

import scala.annotation.tailrec
import scala.concurrent.duration.FiniteDuration
import scala.util.Random

import akka.actor.typed.{
  ActorRef,
  Behavior,
}
import akka.actor.typed.scaladsl.{
  Behaviors, 
  TimerScheduler,
}

import sag.data._

// object Cacher {

// }

// private class Cacher(
//     timer: TimerScheduler[Cacher.Command],
    
//     )