package mongo4cats.bench

import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations._
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Warmup
import cats.syntax.all._
import com.mongodb.internal.connection.ByteBufferBsonOutput
import io.circe.{Decoder, Encoder, Json, ParsingFailure}
import io.circe.syntax._
import io.circe.generic.auto._
import mongo4cats.circe._
import mongo4cats.derivation.bson.AllBsonEncoders._
import mongo4cats.derivation.bson.AllBsonDecoders._
import mongo4cats.derivation.bson.derivation.decoder.auto._
import mongo4cats.derivation.bson.derivation.encoder.auto._
import mongo4cats.derivation.bson.{BsonDecoder, BsonEncoder, BsonValueOps}
import mongo4cats.circe._
import mongo4cats.codecs.MongoCodecProvider
import org.bson.BsonBinaryWriter
import org.bson.codecs.EncoderContext
import org.bson.codecs.configuration.CodecRegistry
import org.bson.io.BasicOutputBuffer

import java.time.Instant
import scala.util.chaining._
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS
import scala.collection.immutable.HashSet

final case class Person(
    firstName: String,
    name: String,
    age: Int,
    address: Address,
    registrationDate: Instant
)

final case class Address(city: String, country: String)

@State(Scope.Thread)
@Fork(1)
@Threads(2)
@Measurement(iterations = 3)
@Warmup(iterations = 2)
@Timeout(time = 20)
class DerivationWriteBench {

  @Param(Array("1000", "100000"))
  var len: Int = _

  var persons: Array[Person] = _

  val encoderContext = EncoderContext.builder().build()
  val bsonEncoder    = BsonEncoder[Person]
  val cp             = deriveCirceCodecProvider[Person]
  val codec          = cp.get.get(classOf[Person], null)
  val output         = new BasicOutputBuffer(100_000_000)

  @Setup
  def setup(): Unit =
    persons = (1 to len).map(i => Person(s"first name $i", s"name $i", i, Address(s"city ${i}", s"country ${i}"), Instant.now())).toArray

  @Benchmark
  def derivation(): Unit = {
    output.truncateToPosition(0)
    val writer = new BsonBinaryWriter(output)
    persons.foreach(p => bsonEncoder.useJavaEncoderFirst(writer, p, encoderContext))
    writer.flush()
  }

  @Benchmark
  def circe(): Unit = {
    output.truncateToPosition(0)
    val writer = new BsonBinaryWriter(output)
    persons.foreach(p => codec.encode(writer, p, encoderContext))
    writer.flush()
  }
}
