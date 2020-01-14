package sag.types

import org.scalatest._

class JoinedCartSpec extends FlatSpec {
    "A joined cart" should "equal to other joined cart if they have same ids and products" in {
        val cart1 = JoinedCart(1, Seq(Product(0, "P0"), Product(1, "P1")))
        val cart2 = JoinedCart(1, Seq(Product(0, "P0"), Product(1, "P1")))

        assert(cart1.equals(cart2))
    }

    "A joined cart" should "not equal to other joined cart if it has different id" in {
        val cart1 = JoinedCart(1, Seq(Product(0, "P0"), Product(1, "P1")))
        val cart2 = JoinedCart(2, Seq(Product(0, "P0"), Product(1, "P1")))

        assert(!cart1.equals(cart2))
    }

    "A  joined cart" should "throw exception when compared to other cart with same id but different products" in {
        val cart1 = JoinedCart(1, Seq(Product(0, "P0"), Product(1, "P1")))
        val cart2 = JoinedCart(1, Seq(Product(0, "P2"), Product(1, "P4")))

        assertThrows[RuntimeException](
            cart1.equals(cart2)
        )
    }
}