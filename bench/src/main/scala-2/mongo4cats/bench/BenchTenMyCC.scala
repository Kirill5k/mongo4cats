package mongo4cats.bench

import cats.syntax.all._
import mongo4cats.bench.ItemST.ItemST2
import mongo4cats.derivation.bson.{BsonDecoder, BsonEncoder, JavaEncoder}
import org.bson.{BsonDocument, BsonString, BsonWriter}
import org.bson.codecs.EncoderContext

import java.time.Instant
import java.time.temporal.ChronoUnit

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

object BenchMyCC extends BaseGen {

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
