package sag.types

case object Product {
    type Id = String
}
case class Product(stockCode: Product.Id, description: String, quantity: Int, unitPrice: Double)
