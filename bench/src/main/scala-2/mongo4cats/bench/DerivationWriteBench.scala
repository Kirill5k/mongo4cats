package mongo4cats.bench

import cats.syntax.all._
import io.circe.syntax._
import io.circe.generic.extras.auto._
import mongo4cats.bench.BenchST.BenchST2
import mongo4cats.bench.DerivationWriteBench._
import mongo4cats.circe._
import mongo4cats.derivation.bson.AllBsonEncoders._
import mongo4cats.derivation.bson.AllBsonDecoders._
import mongo4cats.derivation.bson.configured.encoder.auto._
import mongo4cats.derivation.bson.{BsonDecoder, BsonEncoder, BsonValueOps}
import org.bson.BsonBinaryWriter
import org.bson.codecs.EncoderContext
import org.bson.io.BasicOutputBuffer

import org.openjdk.jmh.annotations._
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS

@State(Scope.Thread)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(1)
@Warmup(iterations = 2)
@Measurement(iterations = 3)
@Timeout(time = 15)
class DerivationWriteBench {

  // @Benchmark
  def a_atdToBytesViaDerivationWithSealedTrait(): Unit = {
    output.truncateToPosition(0)
    bsonEncoder.unsafeBsonEncode(writer, cc2, encoderContext)
  }

  // @Benchmark
  def b_atdToBytesViaCirceWithSealedTrait(): Unit = {
    output.truncateToPosition(0)
    circeCodec.encode(writer, cc2, encoderContext)
  }

  @Benchmark
  def c_writeViaDerivation(): Unit = {
    output.truncateToPosition(0)
    bsonEncoder.unsafeBsonEncode(writer, cc1, encoderContext)
  }

  @Benchmark
  def d_writeViaCirce(): Unit = {
    output.truncateToPosition(0)
    circeCodec.encode(writer, cc1, encoderContext)
  }
}

object DerivationWriteBench {

  implicit val bsonConf = mongo4cats.derivation.bson.configured.Configuration.default
  // .copy(yoloWriteMode = true)
  // .withDiscriminator("theDiscriminator")

  implicit val circeConf = io.circe.generic.extras.Configuration(
    transformMemberNames = bsonConf.transformMemberNames,
    transformConstructorNames = bsonConf.transformConstructorNames,
    useDefaults = bsonConf.useDefaults,
    discriminator = bsonConf.discriminator
  )

  val bsonEncoder    = BsonEncoder[BenchCC]
  val circeCodec     = deriveCirceCodecProvider[BenchCC].get.get(classOf[BenchCC], null)
  val cc1            = BenchCC()
  val cc2            = BenchCC(BenchST2().some)
  val encoderContext = EncoderContext.builder().build()
  val output         = new BasicOutputBuffer(1000)
  val writer         = new BsonBinaryWriter(output)
}
