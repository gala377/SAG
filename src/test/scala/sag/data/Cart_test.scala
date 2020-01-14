package sag.data

import org.scalatest._

class CartSpec extends FlatSpec {
    "A cart" should "equal to other cart if they have same ids and pids" in {
        val cart1 = Cart(1, Seq(1, 2, 3))
        val cart2 = Cart(1, Seq(1, 2, 3))

        assert(cart1.equals(cart2))
    }

    "A cart" should "not equal to cart which has other id" in {
        val cart1 = Cart(1, Seq(1, 2, 3))
        val cart2 = Cart(2, Seq(1, 2, 3))

        assert(!cart1.equals(cart2))
    }

    "A cart" should "throw exception when compared to other cart with same id but different pids" in {
        val cart1 = Cart(1, Seq(1, 2, 3))
        val cart2 = Cart(1, Seq(2, 4, 6))

        assertThrows[RuntimeException](
            cart1.equals(cart2)
        )
    }
}