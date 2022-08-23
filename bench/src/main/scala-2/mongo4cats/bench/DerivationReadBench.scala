package mongo4cats.bench

import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations._
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Warmup
import cats.syntax.all._
import io.circe.Encoder
import io.circe.syntax._
import io.circe.generic.auto._
import mongo4cats.bench.ReadBenchDatas.ST.{ST1, ST2}
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
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS
import scala.util.Random

object ReadBenchDatas {
  final case class CC(
      st: Option[ST] = none,
      s1: String = "case class string 1",
      s2: String = "case class string 2",
      s3: String = "case class string 3",
      s4: String = "case class string 4",
      s5: String = "case class string 5",
      i: Int = 10,
      instant: Instant = Instant.now.truncatedTo(ChronoUnit.MILLIS) // Mongo precision is Millis.
  )

  sealed trait ST
  object ST {
    final case class ST1(st2Short: Short) extends ST
    final case class ST2(
        st1S1: String = "sealed trait string 1",
        st1S2: String = "sealed trait string 2",
        st1S3: String = "sealed trait string 3",
        st1S4: String = "sealed trait string 4",
        st1S5: String = "sealed trait string 5",
        st1I: Int = 20
    ) extends ST
  }
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
  val bsonDecoder: Typeclass[CC]     = BsonDecoder[CC]
  val circeCodec: Codec[CC]          = deriveCirceCodecProvider[CC].get.get(classOf[CC], null)
  val cc1: CC                        = CC(ST2().some)
  val cc2: CC                        = CC()
  def enc(a: CC): ByteBuffer = {
    val buffer = new BasicOutputBuffer(1000)
    circeCodec.encode(new BsonBinaryWriter(buffer), a, encoderContext)
    ByteBuffer.wrap(buffer.toByteArray)
  }

  val byteBuffer1: ByteBuffer = enc(cc1)
  val byteBuffer2: ByteBuffer = enc(cc2)

  @Benchmark
  def a_readViaDerivationWithSealedTrait(): Unit = {
    byteBuffer1.position(0)
    val reader = new BsonBinaryReader(byteBuffer1)
    reader.readBsonType()
    // val decoded = //
    bsonDecoder.unsafeDecode(reader, decoderContext)
    // if (decoded != cc1) throw new Throwable(s"${decoded} != $cc1")
    ()
  }

  @Benchmark
  def b_readViaCirceWithSealedTrait(): Unit = {
    byteBuffer1.position(0)
    val reader = new BsonBinaryReader(byteBuffer1)
    reader.readBsonType()
    // val decoded = //
    circeCodec.decode(reader, decoderContext)
    // if (decoded != cc1) throw new Throwable(s"${decoded} != $cc1")
    ()
  }

  @Benchmark
  def c_readViaDerivation(): Unit = {
    byteBuffer2.position(0)
    val reader = new BsonBinaryReader(byteBuffer2)
    reader.readBsonType()
    // val decoded = //
    bsonDecoder.unsafeDecode(reader, decoderContext)
    // if (decoded != cc2) throw new Throwable(s"${decoded} != $cc2")
    ()
  }

  @Benchmark
  def d_readViaCirce(): Unit = {
    byteBuffer2.position(0)
    val reader = new BsonBinaryReader(byteBuffer2)
    reader.readBsonType()
    // val decoded = //
    circeCodec.decode(reader, decoderContext)
    // if (decoded != cc2) throw new Throwable(s"${decoded} != $cc2")
    ()
  }
}
