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
import org.bson.BsonBinaryWriter
import org.bson.codecs.EncoderContext
import org.bson.io.BasicOutputBuffer

import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS

object WriteBenchDatas {
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
class DerivationWriteBench {
  import WriteBenchDatas._

  val encoderContext = EncoderContext.builder().build()
  val bsonEncoder    = BsonEncoder[Person]
  val circeCodec     = deriveCirceCodecProvider[Person].get.get(classOf[Person], null)
  val output         = new BasicOutputBuffer(1000)
  val writer         = new BsonBinaryWriter(output)
  val person         = Person("first name", "name", 10, Address("city", "country"), Instant.now())

  @Benchmark
  def writeViaDerivation(): Unit = {
    output.truncateToPosition(0)
    bsonEncoder.bsonEncode(writer, person, encoderContext)
  }

  @Benchmark
  def writeViaCirce(): Unit = {
    output.truncateToPosition(0)
    circeCodec.encode(writer, person, encoderContext)
  }
}
