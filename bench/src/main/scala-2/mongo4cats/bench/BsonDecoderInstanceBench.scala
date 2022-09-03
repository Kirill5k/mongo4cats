package mongo4cats.bench

import mongo4cats.bench.BenchMyCC.{fastMyCCBsonDecoder, fastestMyCCBsonEncoder, slowMyCCBsonDecoder, slowMyCCBsonEncoder}
import mongo4cats.bench.BsonDecoderInstanceBench.{fastBsonDecoder, slowBsonDecoder, tenMyCCBytes}
import mongo4cats.bench.BsonEncoderInstanceBench.{fastBsonEncoder, tenMyCC}
import mongo4cats.bench.DerivationReadBench.decoderContext
import mongo4cats.bench.DerivationWriteBench.encoderContext
import mongo4cats.derivation.bson.AllBsonEncoders._
import mongo4cats.derivation.bson.AllBsonDecoders._
import mongo4cats.derivation.bson.derivation.decoder.auto._
import mongo4cats.derivation.bson.{BsonDecoder, BsonEncoder}
import org.bson.codecs.EncoderContext
import org.bson.io.BasicOutputBuffer
import org.bson.{BsonBinaryReader, BsonBinaryWriter, BsonDocument, BsonDocumentReader, BsonDocumentWriter, BsonString}
import org.openjdk.jmh.annotations._

import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

@State(Scope.Thread)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(1)
@Threads(1)
@Measurement(iterations = 3)
@Warmup(iterations = 2)
@Timeout(time = 15)
class BsonDecoderInstanceBench {

  @Setup
  def setup() = {
    val tenMyCCDoc = new BsonDocument()
    fastBsonEncoder.unsafeBsonEncode(new BsonDocumentWriter(tenMyCCDoc), tenMyCC, encoderContext)

    def decodeFromDoc(bsonDecoder: BsonDecoder[BenchTenMyCC]): BenchTenMyCC =
      bsonDecoder.unsafeDecode(new BsonDocumentReader(tenMyCCDoc), decoderContext)

    val fastDecoded = decodeFromDoc(fastBsonDecoder)
    val slowDecoded = decodeFromDoc(slowBsonDecoder)
    // println(fastDoc.toJson)
    if (fastDecoded != tenMyCC) throw new Throwable(s"Bad decoder\n$fastDecoded")
    if (slowDecoded != tenMyCC) throw new Throwable(s"Bad decoder\n$slowDecoded")
  }

  @Benchmark
  def a_fastBsonDecoder(): Unit = {
    tenMyCCBytes.position(0)
    val reader = new BsonBinaryReader(tenMyCCBytes)
    reader.readBsonType()
    // val decoded = //
    fastBsonDecoder.unsafeDecode(reader, decoderContext)
    // if (decoded != tenMyCC) throw new Throwable(s"${decoded} != $tenMyCC")
    ()
  }

  @Benchmark
  def b_slowBsonDecoder(): Unit = {
    tenMyCCBytes.position(0)
    val reader = new BsonBinaryReader(tenMyCCBytes)
    reader.readBsonType()
    // val decoded = //
    slowBsonDecoder.unsafeDecode(reader, decoderContext)
    // if (decoded != tenMyCC) throw new Throwable(s"${decoded} != $tenMyCC")
    ()
  }
}

object BsonDecoderInstanceBench {

  val fastBsonDecoder: BsonDecoder[BenchTenMyCC] = {
    implicit val decoder: BsonDecoder[BenchMyCC] = fastMyCCBsonDecoder
    BsonDecoder[BenchTenMyCC]
  }

  val slowBsonDecoder: BsonDecoder[BenchTenMyCC] = {
    implicit val encoder: BsonDecoder[BenchMyCC] = slowMyCCBsonDecoder
    BsonDecoder[BenchTenMyCC]
  }

  val tenMyCCBytes: ByteBuffer = {
    val buffer = new BasicOutputBuffer(1000)
    fastBsonEncoder.unsafeBsonEncode(new BsonBinaryWriter(buffer), tenMyCC, encoderContext)
    ByteBuffer.wrap(buffer.toByteArray)
  }
}
