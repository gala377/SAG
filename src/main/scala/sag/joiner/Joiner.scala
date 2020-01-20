package sag.joiner

import scala.collection.immutable.{Map, Seq}

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{
  Behaviors, 
  ActorContext,
}

import sag.types._
import sag.types.CacheType
import sag.warehouse.Warehouse
import sag.recorder.Recorder

object Joiner {
    type Carts = Set[Cart];
    type JoinedCarts = Set[JoinedCart];

    final case class State(war: ActorRef[Warehouse.Message], rec: ActorRef[Recorder.Data], pendingCarts: Carts, 
                           pendingJoinedCarts: JoinedCarts, cacheType: Option[CacheType] = None)

    sealed trait Message
    final case class Data(content: Cart) extends Message with CborSerializable
    final case class Products(id: Cart.Id, ps: Seq[Product]) extends Message with CborSerializable
    
    final case class ListCarts(sendTo: ActorRef[ListCartsResponse]) extends Message with CborSerializable
    final case class ListCartsResponse(content: Carts) extends Message with CborSerializable

    final case class StartCaching(cachetype: CacheType) extends Message with CborSerializable
    final case class StopCachingWarehouse(newActor: ActorRef[Warehouse.Message]) extends Message with CborSerializable
    final case class StopCachingRecorder(newActor: ActorRef[Recorder.Data]) extends Message with CborSerializable

    def apply(
        warehouse: ActorRef[Warehouse.Message],
        recorder: ActorRef[Recorder.Data]
    ): Behavior[Message] = 
        new Joiner().listen(State(warehouse, recorder, Set(), Set()))
}

private class Joiner() {
    import Joiner._

    def listen(state: State): Behavior[Message] =
        Behaviors.receive { (ctx, message) => message match {
            case Data(cart) =>
                ctx.log.info(s"Got new cart $cart")
                if (state.cacheType.isEmpty || state.cacheType.get == CacheRecorded) queueCart(ctx, state.war, cart)
                val newPending = state.pendingCarts + cart
                listen(state.copy(pendingCarts=newPending))
            case Products(cartId, ps) =>
                ctx.log.info(s"Got products $ps")
                val newPending = state.pendingCarts.filter(!_.id.equals(cartId))
                var newPendingToSend = state.pendingJoinedCarts
                if (state.cacheType.isEmpty || state.cacheType.get == CacheWarehouse) {
                    state.rec ! Recorder.Data(JoinedCart(cartId, ps))
                } else {
                    newPendingToSend = newPendingToSend + JoinedCart(cartId, ps)
                }                
                listen(state.copy(
                    pendingCarts=newPending, 
                    pendingJoinedCarts=newPendingToSend
                ))
            case ListCarts(sendTo) =>
                sendCarts(sendTo, state.pendingCarts)
                Behaviors.same
            case StartCaching(receivedCacheType: CacheType) =>
                val newCacheType = calculateCacheType(state.cacheType, receivedCacheType)
                listen(state.copy(cacheType=newCacheType))
            case StopCachingWarehouse(newWarehouse) =>
                state.pendingCarts.foreach(c => queueCart(ctx, newWarehouse, c))
                listen(state.copy(war=newWarehouse))
            case StopCachingRecorder(newRecorder) =>
                // Since recorder does not respond we don't know if carts were actually saved
                // TODO: maybe confirmation from Recorder ?
                state.pendingJoinedCarts.foreach(c => state.rec ! Recorder.Data(c))

                listen(state.copy(pendingJoinedCarts=Set())
            case _ =>
                ctx.log.info(s"Unknown message ${message.toString()}")
                Behaviors.same
        }
    }

    def calculateCacheType(currentCacheType: Option[CacheType], newCacheType: CacheType): Option[CacheType] = {
        if (currentCacheType.isEmpty) return Some(newCacheType);
        if (currentCacheType.contains(CacheBoth)) return Some(CacheBoth);
        if (newCacheType == CacheRecorded && currentCacheType.contains(CacheWarehouse)) return Some(CacheBoth)
        if (newCacheType == CacheWarehouse && currentCacheType.contains(CacheRecorded)) return Some(CacheBoth)
        return Some(newCacheType);
    }

    def queueCart(ctx: ActorContext[Message], to: ActorRef[Warehouse.Message], cart: Cart): Unit  = {
        val self = ctx.messageAdapter[Warehouse.Receipt]{
            case Warehouse.Receipt(id, ps) => Products(id, ps)
        }
        ctx.log.info("Placing order")
        to ! Warehouse.Order(cart.id, cart.pids, self)
    }

    def sendCarts(
        to: ActorRef[ListCartsResponse],
        pending: Carts
    ): Unit = to ! ListCartsResponse(pending)
}
