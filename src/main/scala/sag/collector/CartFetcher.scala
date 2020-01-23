package sag.collector

import scala.concurrent.Future
import scala.util.{Try, Success, Failure}
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{Behaviors, ActorContext}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.unmarshalling._
import sag.collector.Collector.CartIsReady
import sag.types.JsonSupport._
import sag.types._

object CartFetcher {
    sealed trait Command

    final case class Download() extends Command with CborSerializable
    final case class Parse(data: Try[HttpResponse]) extends Command with CborSerializable
    final case class SendToCollector(cart: Cart) extends Command with CborSerializable

    def apply(collector: ActorRef[Collector.Command]) : Behavior[Command] =
        Behaviors.setup { ctx =>
            ctx.self ! Download()
            new CartFetcher().listen(collector)
        }    
}

private class CartFetcher() {
    import CartFetcher._

    def listen(collector: ActorRef[Collector.Command]): Behavior[Command] = 
        Behaviors.receive { (ctx: ActorContext[Command], message: Command) => 
            message match {
                case Download() =>
                    val http = Http(ctx.system)
                    val endpoint = "https://young-crag-34985.herokuapp.com/cart"

                    ctx.pipeToSelf(http.singleRequest(HttpRequest(uri = endpoint)))((data) => Parse(data))
                case Parse(data) =>
                    data match {
                        case Success(resp) =>
                            implicit val system = ctx.system
                            ctx.pipeToSelf(Unmarshal(resp.entity).to[Cart])(sendFurtherOrRaise)
                        case Failure(exception) => 
                            ctx.log.info(s"Exception occured when downloading cart: $exception")
                            Behaviors.stopped
                    }
                case SendToCollector(cart) =>
                    collector ! CartIsReady(cart)
                    Behaviors.stopped
            }
            Behaviors.same
        }
        

    def sendFurtherOrRaise(data: Try[Cart]): Command = {
        data match {
            case Success(cart) => SendToCollector(cart)
            case Failure(exception) => 
                throw new RuntimeException(s"Error while parsing cart: $exception")
        }
    }
}