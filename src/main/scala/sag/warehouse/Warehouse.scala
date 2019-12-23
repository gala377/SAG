package sag.warehouse

import scala.collection.immutable.{
    Map,
    Seq,
}
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
    ActorContext,
}

import sag.data._

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

        def addProduct(id: Product.Id, p: Product): OrderState = {
            val newInfo = products + (id -> Some(p))
            if (newInfo.exists{case (_, opt) => opt.isEmpty}) {
                Incompleted(OrderInfo(sender, newInfo))
            } else {
                Completed(OrderInfo(sender, newInfo))
            }
        }
    }

    sealed trait Message
    final case class Order(
        id: Int,
        ps: Seq[Product.Id],
        sendTo: ActorRef[Receipt]
    ) extends Message with CborSerializable
    object Order {
        type Id = Int
    }
    final case class Receipt(
        id: Int,
        ps: Seq[Product]
    ) extends Message with CborSerializable
    private[warehouse] final case class ProductFetched(
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
            case ProductFetched(id, product) => {
                ctx.log.info(s"Fetched product $product for order $id")
                val order = orders.get(id) match {
                    case None => return Behaviors.same;
                    case Some(order) => order
                }
                order.addProduct(product.id, product) match {
                    case OrderInfo.Incompleted(order) => {
                        ctx.log.info(s"Order $id still incompleted")
                        listen(orders + (id -> order))
                    }
                    case OrderInfo.Completed(order) => {
                        ctx.log.info(s"Order $id completed")
                        val newOrders = orders - id
                        order.sender ! Receipt(
                            id,
                            order.products
                                .map{case (_, p) => p}
                                .flatten
                                .toSeq)
                        listen(newOrders) 
                    } 
                }
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

private object ProductFetcher {

    def apply(
        oid: Warehouse.Order.Id,
        pid: Product.Id,
        sendTo: ActorRef[Warehouse.ProductFetched]
    ): Behavior[AnyRef] = Behaviors.setup { ctx =>
        ctx.log.info(s"Product fetcher spawned for order $oid product $pid")
        val product = Products.products(pid)
        ctx.log.info(s"Got product $product. Sending to parent")
        sendTo ! Warehouse.ProductFetched(oid, product)
        Behaviors.stopped
    }
}