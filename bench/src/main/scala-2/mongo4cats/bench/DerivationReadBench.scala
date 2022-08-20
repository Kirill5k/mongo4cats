package mongo4cats.bench

import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations._
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Warmup
import cats.syntax.all._
import io.circe.syntax._
import io.circe.generic.auto._
import mongo4cats.circe._
import mongo4cats.derivation.bson.AllBsonEncoders._
import mongo4cats.derivation.bson.AllBsonDecoders._
import mongo4cats.derivation.bson.derivation.decoder.auto._
import mongo4cats.derivation.bson.derivation.encoder.auto._
import mongo4cats.derivation.bson.{BsonDecoder, BsonEncoder, BsonValueOps}
import org.bson.{BsonBinaryWriter, BsonDocument, BsonDocumentReader}
import org.bson.codecs.{DecoderContext, EncoderContext}
import org.bson.io.BasicOutputBuffer

import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS

object ReadBenchDatas {
  final case class Person(
      firstName: String,
      name: String,
      age: Int,
      address: Address,
      registrationDate: Instant
  )

  final case class Address(city: String, country: String)
}

@State(Scope.Thread)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(2)
@Measurement(iterations = 3)
@Warmup(iterations = 2)
@Timeout(time = 15)
class DerivationReadBench {

  import ReadBenchDatas._

  val decoderContext = DecoderContext.builder().build()
  val bsonDecoder    = BsonDecoder[Person]
  val codec          = deriveCirceCodecProvider[Person].get.get(classOf[Person], null)
  val person         = Person("first name", "name", 10, Address("city", "country"), Instant.now())
  val doc            = new BsonDocument("d", BsonEncoder[Person].toBsonValue(person).asDocument())

  @Benchmark
  def readViaDerivation(): Unit = {
    val docReader = new BsonDocumentReader(doc)
    docReader.readStartDocument()
    docReader.readName()
    bsonDecoder.decode(docReader, decoderContext)
    ()
  }

  @Benchmark
  def readViaCirce(): Unit = {
    val docReader = new BsonDocumentReader(doc)
    docReader.readStartDocument()
    docReader.readName()
    codec.decode(docReader, decoderContext)
    ()
  }
}
