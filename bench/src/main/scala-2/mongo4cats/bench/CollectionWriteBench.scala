package mongo4cats.bench

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.mongodb.client.result.InsertManyResult
import io.circe.generic.auto._
import mongo4cats.circe._
import mongo4cats.models.collection.InsertManyOptions
import org.bson.Document
import org.bson.conversions.Bson
import org.openjdk.jmh.annotations._

import java.util.concurrent.TimeUnit
import scala.collection.mutable
import scala.jdk.CollectionConverters._

@State(Scope.Thread)
@OutputTimeUnit(TimeUnit.SECONDS)
@Fork(1)
@Threads(1)
@Warmup(iterations = 2)
@Measurement(iterations = 3)
@Timeout(time = 15)
class CollectionWriteBench extends BaseCollectionBench {

  val dataSize = 1_000
  val datas    = List.tabulate(dataSize)(i => BenchCC(s1 = s"s1-$i"))

  var fastWriteIO: IO[InsertManyResult]  = _
  var circeWriteIO: IO[InsertManyResult] = _

  @Setup
  override def setup(): Unit = {
    super.setup()
    val options = InsertManyOptions(ordered = false, bypassDocumentValidation = true)
    fastWriteIO = bsonColl.insertMany(datas, options)
    circeWriteIO = circeColl.insertMany(datas, options)
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
