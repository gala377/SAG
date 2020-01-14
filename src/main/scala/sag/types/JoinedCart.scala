package sag.types

case class JoinedCart(id: Cart.Id, products: Seq[Product]) {
    override def equals(o: Any) = o match {
        case that: JoinedCart => {
            if (this.id.equals(that.id) && !this.products.equals(that.products)) {
                throw new RuntimeException("JoinedCarts have same id but different products")
            }

            this.id.equals(that.id)
        }
        case _ => false
    }

    override def hashCode = id.hashCode
}