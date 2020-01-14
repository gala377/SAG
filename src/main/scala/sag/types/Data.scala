package sag.types

import scala.collection.immutable.Seq


case object Product {
    type Id = Int
}
case class Product(id: Product.Id, description: String)

object Products {
    val products: Seq[Product] = Seq(
        Product(0, "P0"),
        Product(1, "P1"),
        Product(2, "P2"),
        Product(3, "P3"),
        Product(4, "P4"),
        Product(5, "P5"),
        Product(6, "P6"),
        Product(7, "P7"),
        Product(8, "P8"),
    )
}

/**
 * Marked trait for the message serialization.
 */
trait CborSerializable