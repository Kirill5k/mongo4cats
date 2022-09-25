package mongo4cats.bench

import cats.syntax.all._
import io.circe.generic.extras.auto._
import io.circe.disjunctionCodecs.encodeEither
import io.circe.disjunctionCodecs.decoderEither
import io.circe.disjunctionCodecs.encodeValidated
import io.circe.disjunctionCodecs.decodeValidated
import mongo4cats.bench.BenchData.data
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
@OutputTimeUnit(TimeUnit.SECONDS)
@Fork(1)
@Threads(1)
@Warmup(iterations = 3)
@Measurement(iterations = 3)
@Timeout(time = 15)
class DerivationReadBench {

  @Benchmark
  def a_bytesToADTViaDerivationRead(): Unit = {
    byteBuffer.position(0)
    val reader = new BsonBinaryReader(byteBuffer)
    reader.readBsonType()
    // val decoded = //
    bsonDecoder.unsafeDecode(reader, decoderContext)
    // if (decoded != benchCCs) throw new Throwable(s"${decoded} != $benchCCs")
    ()
  }

  @Benchmark
  def b_bytesToADTViaCirceRead(): Unit = {
    byteBuffer.position(0)
    val reader = new BsonBinaryReader(byteBuffer)
    reader.readBsonType()
    // val decoded = //
    circeCodec.decode(reader, decoderContext)
    // if (decoded != benchCCs) throw new Throwable(s"${decoded} != $benchCCs")
    ()
  }
}

object DerivationReadBench {

  implicit val bsonConf = mongo4cats.derivation.bson.configured.Configuration.default
  // .withDiscriminator("theDiscriminator")

  implicit val circeConf = io.circe.generic.extras.Configuration(
    transformMemberNames = bsonConf.transformMemberNames,
    transformConstructorNames = bsonConf.transformConstructorNames,
    useDefaults = bsonConf.useDefaults,
    discriminator = bsonConf.discriminator
  )

  val decoderContext: DecoderContext      = DecoderContext.builder().build()
  val bsonDecoder: BsonDecoder[BenchData] = BsonDecoder[BenchData]
  val circeCodec: Codec[BenchData]        = deriveCirceCodecProvider[BenchData].get.get(classOf[BenchData], null)
  val byteBuffer: ByteBuffer = {
    val buffer = new BasicOutputBuffer(7_000_000)
    circeCodec.encode(new BsonBinaryWriter(buffer), data, encoderContext)
    println(s"Buffer position: ${buffer.getPosition}, data.toString.length: ${data.toString.length}")
    ByteBuffer.wrap(buffer.toByteArray)
  }
}
