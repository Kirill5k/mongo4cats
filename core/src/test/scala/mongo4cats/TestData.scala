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
  implicit private val random = Random

  val usdCurrency: Document = Document("symbol" -> "$", "code" -> "USD")
  val gbpCurrency: Document = Document("symbol" -> "£", "code" -> "GBP")
  val eurCurrency: Document = Document("symbol" -> "€", "code" -> "EUR")

  val usdAccount: Document = Document("_id" -> ObjectId.get, "currency" -> usdCurrency, "name" -> "usd-acc")
  val gbpAccount: Document = Document("_id" -> ObjectId.get, "currency" -> gbpCurrency, "name" -> "gbp-acc")
  val eurAccount: Document = Document("_id" -> ObjectId.get, "currency" -> eurCurrency, "name" -> "eur-acc")

  val accounts: Vector[Document]   = Vector(usdAccount, gbpAccount, eurAccount)
  val categories: Vector[Document] = categories(10)

  def transaction: Document =
    Document(
      "_id"      -> ObjectId.get,
      "date"     -> Instant.now().minusMillis(random.nextLong(100000L)),
      "category" -> categories.pickRand,
      "account"  -> usdAccount.getObjectId("_id"),
      "amount"   -> random.nextInt(10000)
    )

  def transactions(n: Int): Vector[Document] = (0 until n).map(_ => transaction).toVector
  def categories(n: Int): Vector[Document] = (0 until n).map(i => Document("_id" -> ObjectId.get, "name" -> s"cat-$i")).toVector

  implicit final private class SeqOps[A](private val seq: Seq[A]) extends AnyVal {
    def pickRand(implicit rnd: Random): A =
      seq(rnd.nextInt(seq.size))
  }
}
