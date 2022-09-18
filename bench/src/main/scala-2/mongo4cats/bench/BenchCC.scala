package mongo4cats.bench

import cats.syntax.all._
import mongo4cats.bench.BenchST.BenchST2
import mongo4cats.derivation.bson.{BsonDecoder, BsonEncoder, JavaEncoder}
import org.bson.{BsonDocument, BsonString, BsonWriter}
import org.bson.codecs.EncoderContext

import java.time.Instant
import java.time.temporal.ChronoUnit

final case class BenchCC(
    st: Option[BenchST] = BenchST2().some,
    s1: String = "case class string 1",
    s2: String = "case class string 2",
    s3: String = "case class string 3",
    s4: String = "case class string 4",
    s5: String = "case class string 5",
    i: Int = 10,
    instant: Instant = Instant.now.truncatedTo(ChronoUnit.MILLIS) // Mongo precision is Millis.
)

sealed trait BenchST
object BenchST {
  final case class BenchST1(st2Short: Short) extends BenchST
  final case class BenchST2(
      st1S1: String = "sealed trait string 1",
      st1S2: String = "sealed trait string 2",
      st1S3: String = "sealed trait string 3",
      st1S4: String = "sealed trait string 4",
      st1S5: String = "sealed trait string 5",
      st1I: Int = 20
  ) extends BenchST
}

final case class BenchTenMyCC(
    myCC0: BenchMyCC = BenchMyCC(),
    myCC1: BenchMyCC = BenchMyCC(),
    myCC2: BenchMyCC = BenchMyCC(),
    myCC3: BenchMyCC = BenchMyCC(),
    myCC4: BenchMyCC = BenchMyCC(),
    myCC5: BenchMyCC = BenchMyCC(),
    myCC6: BenchMyCC = BenchMyCC(),
    myCC7: BenchMyCC = BenchMyCC(),
    myCC8: BenchMyCC = BenchMyCC(),
    myCC9: BenchMyCC = BenchMyCC()
)

final case class BenchMyCC(myValue: String = "abc")

object BenchMyCC {

  val fastestMyCCBsonEncoder: BsonEncoder[BenchMyCC] =
    BsonEncoder.fastInstance { (writer, value) =>
      writer.writeStartDocument()
      writer.writeStartDocument("my-cc")
      writer.writeString("my-value", value.myValue)
      writer.writeEndDocument()
      writer.writeEndDocument()
    }

  val slowMyCCBsonEncoder: BsonEncoder[BenchMyCC] =
    BsonEncoder.slowInstance(v => new BsonDocument("my-cc", new BsonDocument("my-value", new BsonString(v.myValue))))

  val fastMyCCBsonDecoder: BsonDecoder[BenchMyCC] =
    BsonDecoder.fastInstance { reader =>
      reader.readStartDocument()
      reader.readBsonType()
      reader.skipName()
      reader.readStartDocument()
      reader.readBsonType()
      reader.skipName()
      val myValue = reader.readString()
      reader.readEndDocument()
      reader.readEndDocument()
      BenchMyCC(myValue)
    }

  val slowMyCCBsonDecoder: BsonDecoder[BenchMyCC] =
    BsonDecoder.slowInstance { bson =>
      val myValue = bson.asDocument().getDocument("my-cc").getString("my-value").getValue
      BenchMyCC(myValue)
    }
}
