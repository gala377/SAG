package sag.joiner

import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.receptionist
import akka.actor.typed.scaladsl.Behaviors
import sag.{
  actors,
  warehouse,
  recorder,
}
import sag.joiner.Joiner.{
  StartCaching,
  StopCachingWarehouse,
  StopCachingRecorder,
  CacheRecorder,
  CacheWarehouse,
}


object Guardian extends actors.Guardian {

  val ServiceKey: receptionist.ServiceKey[Joiner.Message] =
    receptionist.ServiceKey("Joiner")

  def apply(args: Array[String]): Behavior[Receptionist.Listing] =
    Behaviors.setup { ctx =>
      ctx.system.receptionist ! Receptionist.Subscribe(
        warehouse.Guardian.ServiceKey, ctx.self)
      ctx.system.receptionist ! Receptionist.Subscribe(
        recorder.Guardian.ServiceKey, ctx.self)
      new Guardian().findDependantActors(IncompleteState(None, None))
    }

  final case class IncompleteState(
    rec: Option[ActorRef[recorder.Recorder.Data]],
    war: Option[ActorRef[warehouse.Warehouse.Message]],
  ) {
    def isComplete: Boolean = rec.isDefined && war.isDefined

    def unwrap: (ActorRef[recorder.Recorder.Data], ActorRef[warehouse.Warehouse.Message]) =
      if (isComplete) {
        (rec.get, war.get)
      } else {
        throw new RuntimeException("Trying to unwrap incompleted state")
      }
  }

  final case class WorkingState(
    joiner: ActorRef[Joiner.Message],
    rec: Option[ActorRef[recorder.Recorder.Data]],
    war: Option[ActorRef[warehouse.Warehouse.Message]],
  ) {
    def isComplete: Boolean = rec.isDefined && war.isDefined
  }
}

private class Guardian {

  import Guardian._

  def findDependantActors(
    state: IncompleteState
  ): Behavior[Receptionist.Listing] = Behaviors.receive {
    (ctx, message) =>
      message match {
        case warehouse.Guardian.ServiceKey.Listing(addresses) =>
          findDependantWarehouse(state, addresses, ctx)
        case recorder.Guardian.ServiceKey.Listing(addresses) =>
          findDependantRecorder(state, addresses, ctx)
        case _ => findDependantActors(state)
      }
  }

  def findDependantWarehouse(
    state: IncompleteState,
    addresses: Set[ActorRef[warehouse.Warehouse.Message]],
    ctx: ActorContext[Receptionist.Listing]
  ): Behavior[Receptionist.Listing] =
    if (addresses.isEmpty) {
      ctx.log.info("Warehouses are empty, subscribing to other events")
      findDependantActors(IncompleteState(state.rec, None))
    } else {
      val warRef = addresses.toIndexedSeq(0)
      attemptToSpawnJoiner(
        IncompleteState(state.rec, Some(warRef)),
        ctx)
    }

  def findDependantRecorder(
    state: IncompleteState,
    addresses: Set[ActorRef[recorder.Recorder.Data]],
    ctx: ActorContext[Receptionist.Listing]
  ): Behavior[Receptionist.Listing] =
    if (addresses.isEmpty) {
      ctx.log.info("Recorders are empty, subscribing to other events")
      findDependantActors(IncompleteState(None, state.war))
    } else {
      val recRef = addresses.toIndexedSeq(0)
      attemptToSpawnJoiner(
        IncompleteState(Some(recRef), state.war),
        ctx)
    }

  def attemptToSpawnJoiner(
    state: IncompleteState,
    ctx: ActorContext[Receptionist.Listing]
  ): Behavior[Receptionist.Listing] =
    if (state.isComplete) {
      ctx.log.info("Recorder and warehouse are present. Spawning joiner")
      val (rec, war) = state.unwrap
      val joiner = ctx.spawnAnonymous(Joiner(war, rec))
      ctx.system.receptionist ! Receptionist.Register(
        ServiceKey, joiner)
      monitorDependantActors(WorkingState(joiner, Some(rec), Some(war)))
    } else {
      findDependantActors(state)
    }

  def monitorDependantActors(
    state: WorkingState
  ): Behavior[Receptionist.Listing] = Behaviors.receive {
    (ctx, message) =>
      message match {
        case warehouse.Guardian.ServiceKey.Listing(addresses) =>
          checkWarehouse(state, addresses, ctx)
        case recorder.Guardian.ServiceKey.Listing(addresses) =>
          checkRecorder(state, addresses, ctx)
        case _ => monitorDependantActors(state)
      }
  }

  // TODO: not sure if this is correct
  def checkWarehouse(
    state: WorkingState,
    addresses: Set[ActorRef[warehouse.Warehouse.Message]],
    ctx: ActorContext[Receptionist.Listing]
  ): Behavior[Receptionist.Listing] = {
    if (state.war.isDefined && addresses.contains(state.war.get) == false) {
      ctx.log.warn("Warehouse left the cluster. Changing into caching mode")
      state.joiner ! StartCaching(CacheWarehouse)

      return monitorDependantActors(WorkingState(state.joiner, state.rec, None))
    }

    if (state.war.isEmpty && addresses.nonEmpty) {
      ctx.log.info("Found new warehouse. Stopping caching")

      val war = addresses.toIndexedSeq(0)
      state.joiner ! StopCachingWarehouse(war)
      return monitorDependantActors(WorkingState(state.joiner, state.rec, Some(war)))
    }

    return monitorDependantActors(state)
  }

  def checkRecorder(
    state: WorkingState,
    addresses: Set[ActorRef[recorder.Recorder.Data]],
    ctx: ActorContext[Receptionist.Listing]
  ): Behavior[Receptionist.Listing] = { 
    if (state.rec.isDefined && addresses.contains(state.rec.get) == false) {
      ctx.log.warn("Recorder left the cluster. Changing into caching mode")
      state.joiner ! StartCaching(CacheRecorder)

      return monitorDependantActors(WorkingState(state.joiner, None, state.war))
    }

    if (state.rec.isEmpty && addresses.nonEmpty) {
      ctx.log.info("Found new recorder. Stopping caching")

      val rec = addresses.toIndexedSeq(0)
      state.joiner ! StopCachingRecorder(rec)
      return monitorDependantActors(WorkingState(state.joiner, Some(rec), state.war))
    }

    return monitorDependantActors(state)
  }
}
