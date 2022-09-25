package mongo4cats.bench

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.mongodb.client.result.InsertOneResult
import io.circe.generic.auto._
import io.circe.disjunctionCodecs.encodeEither
import io.circe.disjunctionCodecs.decoderEither
import io.circe.disjunctionCodecs.encodeValidated
import io.circe.disjunctionCodecs.decodeValidated
import mongo4cats.bench.BenchData.data
import mongo4cats.circe._
import mongo4cats.models.collection.InsertOneOptions
import org.openjdk.jmh.annotations._
import org.bson.Document
import org.bson.conversions.Bson

import java.util.concurrent.TimeUnit

@State(Scope.Thread)
@OutputTimeUnit(TimeUnit.SECONDS)
@Fork(1)
@Threads(1)
@Warmup(iterations = 2)
@Measurement(iterations = 3)
@Timeout(time = 15)
class CollectionWriteBench extends BaseCollectionBench {

  var fastWriteIO: IO[InsertOneResult]  = _
  var circeWriteIO: IO[InsertOneResult] = _

  @Setup
  override def setup(): Unit = {
    super.setup()

    val options  = InsertOneOptions(bypassDocumentValidation = true)
    val emptyDoc = new Document()
    fastWriteIO = bsonColl.deleteMany(emptyDoc) *> bsonColl.insertOne(data, options)
    circeWriteIO = circeColl.deleteMany(emptyDoc) *> circeColl.insertOne(data, options)
  }

  @Benchmark
  def a_adtToMongoDbViaDerivationWrite(): Unit = {
    fastWriteIO.unsafeRunSync()(IORuntime.global)
    ()
  }

  @Benchmark
  def b_adtToMongoDbViaCirceWrite(): Unit = {
    circeWriteIO.unsafeRunSync()(IORuntime.global)
    ()
  }
}
