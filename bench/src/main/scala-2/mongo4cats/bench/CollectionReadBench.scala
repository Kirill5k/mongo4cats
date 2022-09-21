package mongo4cats.bench

import cats.effect.IO
import cats.syntax.all._
import cats.effect.syntax.all._
import cats.effect.implicits._
import cats.effect.unsafe.IORuntime
import org.openjdk.jmh.annotations._

import java.time.Instant
import java.util.concurrent.TimeUnit

@State(Scope.Thread)
@OutputTimeUnit(TimeUnit.SECONDS)
@Fork(1)
@Threads(1)
@Warmup(iterations = 2)
@Measurement(iterations = 3)
@Timeout(time = 15)
class CollectionReadBench extends BaseCollectionBench { self =>

  val dataSize = 10_000
  val datas    = Set.tabulate(dataSize)(i => BenchCC(s1 = s"s1-$i"))

  var fastReadIO: IO[Unit]  = _
  var circeReadIO: IO[Unit] = _

  @Setup
  override def setup(): Unit = {
    super.setup()
    fastReadIO = bsonColl.find.all.map(_.size).map(readSize => assert(readSize == dataSize))
    circeReadIO = circeColl.find.all.map(_.size).map(readSize => assert(readSize == dataSize))

    (for {
      _ <- circeColl.insertMany(datas.toSeq)

      readCirce <- circeColl.find.all.map(_.toSet)
      readBson  <- bsonColl.find.all.map(_.toSet)
      _ = assert(readCirce == datas)
      _ = assert(readBson == datas)

    } yield ()).unsafeRunSync()(IORuntime.global)
  }

  @Benchmark
  def a_mongoDbToAdtViaDerivationRead(): Unit =
    fastReadIO.unsafeRunSync()(IORuntime.global)

  @Benchmark
  def b_mongoDbToAdtViaCirce(): Unit =
    circeReadIO.unsafeRunSync()(IORuntime.global)
}
