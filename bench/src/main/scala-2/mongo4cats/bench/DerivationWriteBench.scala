package mongo4cats.bench

import cats.syntax.all._
import io.circe.syntax._
import io.circe.generic.extras.auto._
import mongo4cats.bench.BenchData.data
import mongo4cats.bench.ItemST.ItemST2
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
@OutputTimeUnit(TimeUnit.SECONDS)
@Fork(1)
@Threads(1)
@Warmup(iterations = 2)
@Measurement(iterations = 3)
@Timeout(time = 15)
class DerivationWriteBench {

  @Benchmark
  def c_writeViaDerivationWrite(): Unit = {
    output.truncateToPosition(0)
    bsonEncoder.unsafeBsonEncode(writer, data, encoderContext)
  }

  @Benchmark
  def d_writeViaCirceWrite(): Unit = {
    output.truncateToPosition(0)
    circeCodec.encode(writer, data, encoderContext)
  }
}

object DerivationWriteBench {

  implicit val bsonConf = mongo4cats.derivation.bson.configured.Configuration.default
  // .withDiscriminator("theDiscriminator")

  implicit val circeConf = io.circe.generic.extras.Configuration(
    transformMemberNames = bsonConf.transformMemberNames,
    transformConstructorNames = bsonConf.transformConstructorNames,
    useDefaults = bsonConf.useDefaults,
    discriminator = bsonConf.discriminator
  )

  val bsonEncoder    = BsonEncoder[BenchData]
  val circeCodec     = deriveCirceCodecProvider[BenchData].get.get(classOf[BenchData], null)
  val encoderContext = EncoderContext.builder().build()
  val output         = new BasicOutputBuffer(7_000_000)
  val writer         = new BsonBinaryWriter(output)
}
