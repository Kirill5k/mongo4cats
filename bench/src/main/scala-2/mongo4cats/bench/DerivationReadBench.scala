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
import mongo4cats.derivation.bson.derivation.decoder.auto.{Typeclass, _}
import mongo4cats.derivation.bson.derivation.encoder.auto._
import mongo4cats.derivation.bson.{AllBsonDecoders, BsonDecoder, BsonEncoder, BsonValueOps}
import org.bson.{BsonBinaryReader, BsonBinaryWriter, BsonDocument, BsonDocumentReader, BsonDocumentWriter, BsonValue}
import org.bson.codecs.{BsonValueCodec, Codec, DecoderContext, EncoderContext}
import org.bson.io.BasicOutputBuffer
import org.bson.json.JsonWriter
import org.openjdk.jmh.infra.Blackhole

import java.nio.ByteBuffer
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS
import scala.util.Random

object ReadBenchDatas {
  final case class Person(
      firstName: String,
      name: String,
      age: Int,
      address: Address,
      registrationDate: Instant
      // registrationDate: String
  )

  final case class Address(city: String, country: String)
}

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(1)
@Measurement(iterations = 3)
@Warmup(iterations = 2)
@Timeout(time = 15)
class DerivationReadBench {

  import ReadBenchDatas._

  val encoderContext: EncoderContext = EncoderContext.builder().build()
  val decoderContext: DecoderContext = DecoderContext.builder().build()
  val bsonDecoder: Typeclass[Person] = BsonDecoder[Person]
  val circeCodec: Codec[Person]      = deriveCirceCodecProvider[Person].get.get(classOf[Person], null)
  val person: Person                 = Person("first name", "name", 10, Address("city", "country"), Instant.now())
  val byteBuffer: ByteBuffer = {
    val outputBuffer = new BasicOutputBuffer(1000)
    circeCodec.encode(new BsonBinaryWriter(outputBuffer), person, encoderContext)
    ByteBuffer.wrap(outputBuffer.toByteArray)
  }

  @Benchmark
  def readViaDerivation(): Unit = {
    byteBuffer.position(0)
    val reader = new BsonBinaryReader(byteBuffer)
    reader.readBsonType()
    // val decoded = //
    bsonDecoder.unsafeDecode(reader, decoderContext)
    // if (decoded != person) throw new Throwable(s"${decoded} != $person")
    ()
  }

  @Benchmark
  def readViaCirce(): Unit = {
    byteBuffer.position(0)
    val reader = new BsonBinaryReader(byteBuffer)
    reader.readBsonType()
    // val decoded = //
    circeCodec.decode(reader, decoderContext)
    // if (decoded != person) throw new Throwable(s"${decoded} != $person")
    ()
  }
}
