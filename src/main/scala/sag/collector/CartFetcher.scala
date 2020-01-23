package sag.collector

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success, Try}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.unmarshalling._
import sag.collector.Collector.CartIsReady
import sag.payload.JsonSupport._
import sag.payload._

object CartFetcher {
    sealed trait Message
    final case class CartFetched(cart: Cart) extends Message with CborSerializable
    final case class FetchingFailure(error: String) extends Message with CborSerializable

    def apply(collector: ActorRef[Collector.Command]) : Behavior[Message] =
        Behaviors.setup { ctx =>
            new CartFetcher(collector).download(ctx)
        }    
}

private class CartFetcher(sendTo: ActorRef[Collector.Command]) {
    import CartFetcher._

    def download(ctx: ActorContext[Message]): Behavior[Message] = {
        val endpoint = "https://young-crag-34985.herokuapp.com/cart"
        implicit val ec: ExecutionContextExecutor = ctx.system.executionContext
        implicit val system: ActorSystem[Nothing] = ctx.system
        val req = Http(ctx.system)
          .singleRequest(HttpRequest(uri = endpoint))
          .flatMap(resp => Unmarshal(resp.entity).to[Cart])
        ctx.pipeToSelf(req) {
            case Success(c) => CartFetched(c)
            case Failure(e) => FetchingFailure(e.toString)
        }
        listen()
    }

    def listen(): Behavior[Message] = Behaviors.receiveMessage {
        case CartFetched(c) =>
            sendTo ! Collector.CartIsReady(c)
            Behaviors.stopped
        case FetchingFailure(_) =>
            // Todo do smth?
            Behaviors.stopped
    }

}