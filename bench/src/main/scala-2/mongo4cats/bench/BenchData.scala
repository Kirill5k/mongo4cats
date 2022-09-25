package mongo4cats.bench

import cats.data.Validated
import cats.syntax.all._
import org.scalacheck.{Arbitrary, Gen}
import org.scalacheck.ScalacheckShapeless._
import org.scalacheck.cats.implicits._

import java.time.Instant
import java.util.UUID

trait BaseGen {
  implicit val instantArb: Arbitrary[Instant] =
    Arbitrary(Gen.choose(0, 1000000L).map(Instant.ofEpochSecond))

  implicit val objectIdArb: Arbitrary[org.bson.types.ObjectId] =
    Arbitrary((Gen.choose(0, 16777215), Gen.choose(0, 16777215)).mapN(new org.bson.types.ObjectId(_, _)))

  implicit val shortStringArb: Arbitrary[String] =
    Arbitrary(Gen.choose(0, 15) >>= (Gen.stringOfN(_, Gen.alphaNumChar)))
}

final case class BenchData(items: List[Items])

object BenchData extends BaseGen {
  val data: BenchData = Gen.listOfN(30_000, Arbitrary.arbitrary[Items]).map(BenchData(_)).sample.get
}

final case class Items(
    st: ItemST,
    s: String,
    c: Char,
    b: Byte,
    short: Short,
    i: Int,
    l: Long,
    id: UUID,
    time: Instant
)

sealed trait ItemST

object ItemST {
  final case object ItemCObj extends ItemST

  final case class ItemST1(
      sh: Short,
      i: Int,
      b: Byte,
      map: Map[Int, Long]
  ) extends ItemST

  final case class ItemST2(
      s: String,
      intOpt: Option[Int],
      int: Int,
      //either: Either[Long, String],
      //validated: Validated[Int, Char]
  ) extends ItemST
}
