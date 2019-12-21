package sag.data

import scala.collection.immutable

case object Product {
    type Id = Int
}
case class Product(description: String)

case object Cart {
    type Id = Int
}
case class Cart(id: Cart.Id, pids: Seq[Product.Id])

object Products {
    val products: Seq[Product] = Seq(
        Product("P1"),
        Product("P2"),
        Product("P3"),
        Product("P4"),
        Product("P5"),
        Product("P6"),
        Product("P7"),
        Product("P8"),
        Product("P9"),
    )
}

/**
 * Marked trait for the message serialization.
 */
trait CborSerializable