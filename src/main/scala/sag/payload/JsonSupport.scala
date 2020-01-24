package sag.payload

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.DefaultJsonProtocol

object JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
    implicit val cartFormat = jsonFormat2(Cart.apply)
    implicit val productFormat = jsonFormat4(Product.apply)
    implicit val joinedCartFormat = jsonFormat2(JoinedCart.apply)
}