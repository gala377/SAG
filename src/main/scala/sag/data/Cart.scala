package sag.data

case object Cart {
    type Id = Int
}

case class Cart(id: Cart.Id, pids: Seq[Product.Id]) {
    override def equals(o: Any) = o match {
        case that: Cart => {
            if (this.id.equals(that.id) && !this.pids.equals(that.pids)) {
                throw new RuntimeException("Carts have same id but different pids")
            }

            this.id.equals(that.id)
        }
        case _ => false
    }

    override def hashCode = id.hashCode
}