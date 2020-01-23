package sag.payload


/**
 * Marked trait for the message serialization.
 */
trait CborSerializable


case object Cart {
  type Id = String
}

case class Cart(id: Cart.Id, pids: Seq[Product.Id]) {
  override def equals(o: Any): Boolean = o match {
    case that: Cart =>
      if (this.id.equals(that.id) && !this.pids.equals(that.pids)) {
        throw new RuntimeException("Carts have same id but different pids")
      }
      this.id.equals(that.id)
    case _ => false
  }

  override def hashCode: Int = id.hashCode
}


case class JoinedCart(id: Cart.Id, products: Seq[Product]) {

  override def equals(o: Any): Boolean = o match {
    case that: JoinedCart =>
      if (this.id.equals(that.id) && !this.products.equals(that.products)) {
        throw new RuntimeException("JoinedCarts have same id but different products")
      }
      this.id.equals(that.id)
    case _ => false
  }

  override def hashCode: Int = id.hashCode
}


case object Product {
  type Id = String
}

case class Product(
  stockCode: Product.Id,
  description: String,
  quantity: Int,
  unitPrice: Double,
)
