package mongo4cats.bench

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import io.circe.generic.auto._
import mongo4cats.circe._
import mongo4cats.derivation.bson.Fast
import org.bson.Document
import org.bson.conversions.Bson
import org.openjdk.jmh.annotations._

import java.util.concurrent.TimeUnit

@State(Scope.Thread)
@OutputTimeUnit(TimeUnit.SECONDS)
@Fork(1)
@Threads(1)
@Warmup(iterations = 2)
@Measurement(iterations = 3)
@Timeout(time = 15)
class CollectionWriteBench extends BaseCollectionBench {

  var fastWriteIO: IO[Unit]  = _
  var circeWriteIO: IO[Unit] = _

  @Setup
  override def setup(): Unit = {
    super.setup()

    val dataSize  = 10_000
    val datas     = Seq.tabulate(dataSize)(i => BenchCC(s1 = s"s1-$i"))
    val fastDatas = datas.asInstanceOf[Seq[Fast[BenchCC]]]
    val emptyDoc  = new Document()

    fastWriteIO = /* bsonColl.deleteMany(emptyDoc) *> */ bsonColl.insertMany(fastDatas).void
    circeWriteIO = /* circeColl.deleteMany(emptyDoc) *> */ circeColl.insertMany(datas).void
  }

  @Benchmark
  def a_fastWrite(): Unit = {
    fastWriteIO.unsafeRunSync()(IORuntime.global)
    ()
  }

  @Benchmark
  def b_circeWrite(): Unit = {
    circeWriteIO.unsafeRunSync()(IORuntime.global)
    ()
  }
}
