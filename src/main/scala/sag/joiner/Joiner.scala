package sag.joiner

import scala.collection.immutable.Seq

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{
  Behaviors, 
  ActorContext,
}

import sag.types._
import sag.warehouse.Warehouse
import sag.recorder.Recorder
import scala.annotation.tailrec

object Joiner {
    type Carts = Set[Cart]
    type JoinedCarts = Set[JoinedCart]

    sealed trait CacheType
    // Cache data meant to be send to warehouse
    case object CacheWarehouse extends CacheType
    // Cache data meant to be send to recorder
    case object CacheRecorder extends CacheType
    // Cache both types of data 
    case object CacheBoth extends CacheType

    final case class State(
        war: Option[ActorRef[Warehouse.Message]], 
        rec: Option[ActorRef[Recorder.Data]],
        pendingCarts: Carts, 
        pendingJoinedCarts: JoinedCarts,
        cacheType: Option[CacheType])

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
        new Joiner().listen(
            State(
                Some(warehouse),
                Some(recorder),
                Set(),
                Set(),
                None
            )
        )
}

private class Joiner() {
    import Joiner._

    def listen(state: State): Behavior[Message] =
        Behaviors.receive { (ctx, message) => message match {
            case Data(cart) =>
                ctx.log.info(s"Got new cart $cart")
                state.cacheType match {
                    case None | Some(CacheRecorder) =>
                        queueCart(ctx, state.war.get, cart)
                    case _=>
                }        
                val newPending = state.pendingCarts + cart
                listen(state.copy(pendingCarts=newPending))
            case Products(cartId, ps) =>
                ctx.log.info(s"Got products $ps")
                val newPending = state.pendingCarts.filter(_.id != cartId)
                val newPendingToSend = state.cacheType match {
                    case None | Some(CacheWarehouse) => 
                        state.pendingCarts
                            .find(_.id == cartId)
                            .flatMap(joinCart(_, ps.toSet)) match {
                                case Some(joinedCart) => state.rec.get ! Recorder.Data(joinedCart)
                                case None => ctx.log.error(s"Couldn't complete matching cart $cartId")
                        }
                        state.pendingJoinedCarts
                    case default => state.pendingJoinedCarts + JoinedCart(cartId, ps)
                }               
                listen(state.copy(
                    pendingCarts=newPending, 
                    pendingJoinedCarts=newPendingToSend
                ))
            case ListCarts(sendTo) =>
                sendCarts(sendTo, state.pendingCarts)
                Behaviors.same
            case StartCaching(receivedCacheType: CacheType) =>
                val newCacheType = increamentCacheType(state.cacheType, receivedCacheType)
                newCacheType match {
                    case Some(CacheRecorder) => 
                        listen(state.copy(rec=None, cacheType=newCacheType))
                    case Some(CacheWarehouse) =>
                        listen(state.copy(war=None, cacheType=newCacheType))
                    case Some(CacheBoth) =>
                        listen(state.copy(rec=None, war=None, cacheType=newCacheType))
                    case _ => Behaviors.same
                    // ^ this one should never happen 
                }
            case StopCachingWarehouse(newWarehouse) =>
                state.pendingCarts.foreach(c => queueCart(ctx, newWarehouse, c))
                val newCacheType = decreamentCacheType(state.cacheType, CacheWarehouse)
                listen(state.copy(war=Some(newWarehouse), cacheType=newCacheType))
            case StopCachingRecorder(newRecorder) =>
                // Since recorder does not respond we don't know if carts were actually saved
                // TODO: maybe confirmation from Recorder ?
                state.pendingJoinedCarts.foreach(c => newRecorder ! Recorder.Data(c))
                val newCacheType = decreamentCacheType(state.cacheType, CacheRecorder)
                listen(state.copy(
                    rec=Some(newRecorder),
                    pendingJoinedCarts=Set(),
                    cacheType=newCacheType,
                ))
            case _ =>
                ctx.log.info(s"Unknown message ${message.toString()}")
                Behaviors.same
        }
    }

    def increamentCacheType(
        currCacheType: Option[CacheType],
        otherCacheType: CacheType 
    ): Option[CacheType] = currCacheType match {
        case Some(CacheBoth) => Some(CacheBoth)
        case Some(CacheWarehouse) if otherCacheType == CacheRecorder => 
            Some(CacheBoth)
        case Some(CacheRecorder) if otherCacheType == CacheWarehouse => 
            Some(CacheBoth)
        case _ => Some(otherCacheType),
    }

    def decreamentCacheType(
        currCacheType: Option[CacheType],
        otherCacheType: CacheType 
    ): Option[CacheType] = currCacheType match {
        case Some(CacheBoth) if otherCacheType == CacheWarehouse =>
            Some(CacheRecorder)
        case Some(CacheBoth) if otherCacheType == CacheRecorder =>
            Some(CacheWarehouse)
        case Some(CacheWarehouse) if otherCacheType == CacheWarehouse => 
            None
        case Some(CacheRecorder) if otherCacheType == CacheRecorder => 
            None
        case _ => None,
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

    def joinCart(cart: Cart, products: Set[Product]): Option[JoinedCart] = {
        @tailrec
        def _joinCart(
            ids: Seq[Product.Id], 
            ps: Set[Product],
            res: JoinedCart,
        ): Option[JoinedCart] = {
            ids match {
                case x :: idsTail => {
                    val p = ps.find(_.id == x) match {
                        case Some(p) => p
                        case None => return None;
                    }
                    _joinCart(
                        idsTail,
                        ps, 
                        res.copy(products = res.products :+ p),
                    )
                }
                case Nil => Some(res),
            }
        }
        _joinCart(cart.pids, products, JoinedCart(cart.id, Seq()))
    }
}
