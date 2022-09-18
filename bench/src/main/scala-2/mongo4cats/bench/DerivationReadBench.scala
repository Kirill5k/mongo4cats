package mongo4cats.bench

import cats.syntax.all._
import io.circe.generic.extras.auto._
import mongo4cats.bench.BenchST.BenchST2
import mongo4cats.bench.DerivationReadBench._
import mongo4cats.bench.DerivationWriteBench.encoderContext
import mongo4cats.circe._
import mongo4cats.derivation.bson.AllBsonEncoders._
import mongo4cats.derivation.bson.AllBsonDecoders._
import mongo4cats.derivation.bson.BsonDecoder
import mongo4cats.derivation.bson.configured.decoder.auto._
import org.bson.codecs.{Codec, DecoderContext, EncoderContext}
import org.bson.io.BasicOutputBuffer
import org.bson.{BsonBinaryReader, BsonBinaryWriter}

import org.openjdk.jmh.annotations._
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(1)
@Warmup(iterations = 3)
@Measurement(iterations = 3)
@Timeout(time = 15)
class DerivationReadBench {

  // @Benchmark
  def a_readViaDerivationWithSealedTrait(): Unit = {
    byteBuffer1.position(0)
    val reader = new BsonBinaryReader(byteBuffer1)
    reader.readBsonType()
    // val decoded = //
    bsonDecoder.unsafeDecode(reader, decoderContext)
    // if (decoded != cc1) throw new Throwable(s"${decoded} != $cc1")
    ()
  }

  // @Benchmark
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

object DerivationReadBench {

  implicit val bsonConf = mongo4cats.derivation.bson.configured.Configuration.default
  // .withDiscriminator("theDiscriminator")

  // println(bsonConf)

  implicit val circeConf = io.circe.generic.extras.Configuration(
    transformMemberNames = bsonConf.transformMemberNames,
    transformConstructorNames = bsonConf.transformConstructorNames,
    useDefaults = bsonConf.useDefaults,
    discriminator = bsonConf.discriminator
  )

  val decoderContext: DecoderContext  = DecoderContext.builder().build()
  val bsonDecoder: Typeclass[BenchCC] = BsonDecoder[BenchCC]
  val circeCodec: Codec[BenchCC]      = deriveCirceCodecProvider[BenchCC].get.get(classOf[BenchCC], null)
  val cc1: BenchCC                    = BenchCC(BenchST2().some)
  val cc2: BenchCC                    = BenchCC()

  def enc(a: BenchCC): ByteBuffer = {
    val buffer = new BasicOutputBuffer(1000)
    circeCodec.encode(new BsonBinaryWriter(buffer), a, encoderContext)
    ByteBuffer.wrap(buffer.toByteArray)
  }

  val byteBuffer1: ByteBuffer = enc(cc1)
  val byteBuffer2: ByteBuffer = enc(cc2)
}
