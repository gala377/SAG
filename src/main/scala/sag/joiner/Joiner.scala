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
        new Joiner(warehouse, recorder).listen(Set(), Set())
}

private class Joiner(
    var warehouse: ActorRef[Warehouse.Message],
    var recorder: ActorRef[Recorder.Data]) 
{
    import Joiner._

    def listen(pending: Carts, pendingToSend: JoinedCarts, cacheType: Option[CacheType] = None): Behavior[Message] =
        Behaviors.receive { (ctx, message) => message match {
            case Data(cart) =>
                ctx.log.info(s"Got new cart $cart")
                if (cacheType.isEmpty || cacheType.get == CacheRecorded) queueCart(ctx, cart)
                val newPending = pending + cart
                listen(newPending, pendingToSend, cacheType)
            case Products(cartId, ps) =>
                ctx.log.info(s"Got products $ps")
                val newPending = pending.filter(!_.id.equals(cartId))
                var newPendingToSend = pendingToSend
                if (cacheType.isEmpty || cacheType.get == CacheWarehouse) {
                    recorder ! Recorder.Data(JoinedCart(cartId, ps))
                } else {
                    newPendingToSend = newPendingToSend + JoinedCart(cartId, ps)
                }                
                listen(newPending, newPendingToSend, cacheType)
            case ListCarts(sendTo) =>
                sendCarts(sendTo, pending)
                Behaviors.same
            case StartCaching(receivedCacheType: CacheType) =>
                val newCacheType = calculateCacheType(cacheType, receivedCacheType)
                listen(pending, pendingToSend, newCacheType)
            case StopCachingWarehouse(newWarehouse) =>
                warehouse = newWarehouse
                pending.foreach(c => queueCart(ctx, c))
                listen(pending, pendingToSend)
            case StopCachingRecorder(newRecorder) =>
                recorder = newRecorder

                // Since recorder does not respond we don't know if carts were actually saved
                // TODO: maybe confirmation from Recorder ?
                pendingToSend.foreach(c => recorder ! Recorder.Data(c))

                listen(pending, Set())
            case _ => Behaviors.same
        }
    }

    def calculateCacheType(currentCacheType: Option[CacheType], newCacheType: CacheType): Option[CacheType] = {
        if (currentCacheType.isEmpty) return Some(newCacheType);
        if (currentCacheType.contains(CacheBoth)) return Some(CacheBoth);
        if (newCacheType == CacheRecorded && currentCacheType.contains(CacheWarehouse)) return Some(CacheBoth)
        if (newCacheType == CacheWarehouse && currentCacheType.contains(CacheRecorded)) return Some(CacheBoth)
        return Some(newCacheType);
    }

    def queueCart(ctx: ActorContext[Message], cart: Cart): Unit  = {
        val self = ctx.messageAdapter[Warehouse.Receipt]{
            case Warehouse.Receipt(id, ps) => Products(id, ps)
        }
        ctx.log.info("Placing order")
        warehouse ! Warehouse.Order(cart.id, cart.pids, self)
    }

    def sendCarts(
        to: ActorRef[ListCartsResponse],
        pending: Carts
    ): Unit = to ! ListCartsResponse(pending)
}