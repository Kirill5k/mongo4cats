package mongo4cats

import mongo4cats.bson.{Document, ObjectId}

import java.time.Instant
import scala.util.Random

final case class Account(
    id: ObjectId
)

final case class Category(
    id: ObjectId,
    name: String
)

final case class Transaction(
    id: ObjectId,
    amount: BigDecimal,
    account: ObjectId,
    category: ObjectId,
    date: Instant
)

object TestData {
  private val random = Random

  val usdCurrency: Document        = Document("symbol" -> "$", "code" -> "USD")
  val account: Document            = Document("_id" -> ObjectId.get, "currency" -> usdCurrency)
  val categories: Vector[Document] = (0 until 10).map(i => Document("_id" -> ObjectId.get, "name" -> s"cat-$i")).toVector

  def transaction: Document =
    Document(
      "_id"      -> ObjectId.get,
      "date"     -> Instant.now().minusMillis(random.nextLong(100000L)),
      "category" -> categories(random.nextInt(categories.size)),
      "account"  -> account.getObjectId("_id"),
      "amount"   -> random.nextInt(10000)
    )

  def transactions(n: Int): Vector[Document] = (0 until n).map(_ => transaction).toVector
}
