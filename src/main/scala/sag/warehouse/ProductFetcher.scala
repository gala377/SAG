package sag.warehouse

import scala.util.{Failure, Success, Try}
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.unmarshalling._
import sag.warehouse.Warehouse.ProductFetched
import sag.types.JsonSupport._
import sag.types._

object ProductFetcher {
    sealed trait Command
    final case class Download() extends Command with CborSerializable
    final case class Parse(data: Try[HttpResponse]) extends Command with CborSerializable
    final case class SendToWarehouse(product: Product) extends Command with CborSerializable

    def apply(id: String, stockCode: String, warehouse: ActorRef[Warehouse.Message]) : Behavior[Command] =
        Behaviors.setup { ctx =>
            ctx.self ! Download()
            new ProductFetcher().listen(id, stockCode, warehouse)
        }    
}

private class ProductFetcher() {
    import ProductFetcher._

    def listen(id: String, stockCode: String, warehouse: ActorRef[Warehouse.Message]): Behavior[Command] = 
        Behaviors.receive { (ctx: ActorContext[Command], message: Command) => 
            message match {
                case Download() =>
                    val http = Http(ctx.system)
                    val endpoint = s"https://nameless-citadel-79852.herokuapp.com/products/$stockCode"

                    ctx.pipeToSelf(http.singleRequest(HttpRequest(uri = endpoint)))((data) => Parse(data))
                case Parse(data) =>
                    data match {
                        case Success(resp) =>
                            implicit val system = ctx.system
                            ctx.pipeToSelf(Unmarshal(resp.entity).to[Product])(sendFurtherOrRaise)
                        case Failure(exception) => 
                            ctx.log.info(s"Exception occured when downloading product: $exception")
                            Behaviors.stopped
                    }
                case SendToWarehouse(product) =>
                    warehouse ! ProductFetched(id, product)
                    Behaviors.stopped
            }
            Behaviors.same
        }
        

    def sendFurtherOrRaise(data: Try[Product]): Command = {
        data match {
            case Success(product) => SendToWarehouse(product)
            case Failure(exception) => 
                throw new RuntimeException(s"Error while parsing product: $exception")
        }
    }
}