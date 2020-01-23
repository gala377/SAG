package sag.warehouse

import scala.collection.immutable.{Map, Seq}

import akka.actor.typed.{
    ActorRef,
    Behavior,
}
import akka.actor.typed.scaladsl.{
    Behaviors,
    ActorContext,
}

import sag.types._


object Warehouse {

    private[warehouse] type Orders = Map[Order.Id, OrderInfo]
    
    private[warehouse] object OrderInfo {

        sealed trait OrderState
        final case class Completed(order: OrderInfo) extends OrderState
        final case class Incompleted(order: OrderInfo) extends OrderState

        def apply(sendTo: ActorRef[Receipt], ps: Seq[Product.Id]): OrderInfo = {
            new OrderInfo(
                sendTo,
                ps.map{_ -> None}.toMap)
        }
    }

    final private[warehouse] case class OrderInfo(
        sender: ActorRef[Receipt],
        products: Map[Product.Id, Option[Product]])
    {
        import OrderInfo._

        def addProduct(p: Product): OrderState = {
            val newInfo = products + (p.stockCode -> Some(p))
            if (newInfo.exists{case (_, opt) => opt.isEmpty}) {
                Incompleted(OrderInfo(sender, newInfo))
            } else {
                Completed(OrderInfo(sender, newInfo))
            }
        }
    }

    sealed trait Message
    final case class Order(
        id: String,
        ps: Seq[Product.Id],
        sendTo: ActorRef[Receipt]
    ) extends Message with CborSerializable
    object Order {
        type Id = String
    }
    final case class Receipt(
        id: String,
        ps: Seq[Product]
    ) extends Message with CborSerializable
    final case class ProductFetched(
        id: Order.Id,
        product: Product
    ) extends Message with CborSerializable

    def apply(): Behavior[Message] =
        new Warehouse().listen(Map())
}

private class Warehouse {

    import Warehouse._

    def listen(orders: Orders): Behavior[Message] = Behaviors.receive {
        (ctx, message) => message match {
            case Order(id, ps, sender) => {
                ctx.log.info(s"Order received $id: $ps")
                val newOrders = orders + (id -> OrderInfo(sender, ps))
                queueOrder(ctx, id, OrderInfo(sender, ps))
                listen(newOrders)
            }
            case ProductFetched(id, product) =>
                ctx.log.info(s"Fetched product $product for order $id")
                orders.get(id).flatMap(x => Some(x.addProduct(product))) match {
                    case None => Behaviors.same
                    case Some(OrderInfo.Incompleted(order)) =>
                        ctx.log.info(s"Order $id still incompleted")
                        ctx.log.info(s"Order state $order")
                        listen(orders + (id -> order))
                    case Some(OrderInfo.Completed(order)) =>
                        ctx.log.info(s"Order $id completed")
                        order.sender ! Receipt(
                            id,
                            order.products
                            .flatMap { case (_, p) => p }
                            .toSeq)
                        listen(orders - id)
                }
            case _ => Behaviors.same
        }
    }

    def queueOrder(
        ctx: ActorContext[Message],
        id: Order.Id,
        order: OrderInfo): Unit =
    {
        for(pid <- order.products.keys) {
            ctx.log.info(s"Spawning fetcher for order: $id, product: $pid")
            ctx.spawnAnonymous(ProductFetcher(id, pid, ctx.self))
        }
    }
}
