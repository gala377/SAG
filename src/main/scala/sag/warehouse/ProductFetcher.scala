package sag.warehouse

import scala.concurrent.ExecutionContextExecutor
import scala.util.{Failure, Success}

import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.unmarshalling.Unmarshal

import sag.types.{Product, CborSerializable}
import sag.types.JsonSupport._


object ProductFetcher {
    sealed trait Message
    final case class ProductFetched(product: Product) extends Message with CborSerializable
    final case class FetchingFailure(error: String) extends  Message with CborSerializable

    def apply(id: String, stockCode: String, warehouse: ActorRef[Warehouse.Message]) : Behavior[Message] =
        Behaviors.setup { ctx =>
            new ProductFetcher(id, stockCode, warehouse)
              .download(ctx)
        }
}

private class ProductFetcher(
    id: String,
    stockCode: String,
    warehouse: ActorRef[Warehouse.Message]
) {
    import ProductFetcher._

    def download(ctx: ActorContext[Message]): Behavior[Message] = {
        val endpoint = s"https://nameless-citadel-79852.herokuapp.com/products/$stockCode"
        implicit val ec: ExecutionContextExecutor = ctx.system.executionContext
        implicit val system: ActorSystem[Nothing] = ctx.system
        val req = Http(ctx.system)
          .singleRequest(HttpRequest(uri = endpoint))
          .flatMap(resp => Unmarshal(resp.entity).to[Product])
        ctx.pipeToSelf(req) {
            case Success(p) => ProductFetched(p)
            case Failure(e) => FetchingFailure(e.toString)
        }
        listen()
    }

    def listen(): Behavior[Message] = Behaviors.receiveMessage {
            case ProductFetched(p) =>
                warehouse ! Warehouse.ProductFetched(id, p)
                Behaviors.stopped
            case FetchingFailure(_) =>
                // TODO: Send info to warehouse that the fetching failed
                Behaviors.stopped
    }
}