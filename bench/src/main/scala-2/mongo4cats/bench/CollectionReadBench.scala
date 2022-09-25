package mongo4cats.bench

import cats.effect.IO
import cats.syntax.all._
import cats.effect.syntax.all._
import cats.effect.implicits._
import cats.effect.unsafe.IORuntime
import io.circe.disjunctionCodecs.encodeEither
import io.circe.disjunctionCodecs.decoderEither
import io.circe.disjunctionCodecs.encodeValidated
import io.circe.disjunctionCodecs.decodeValidated
import mongo4cats.bench.BenchData.data
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

  var fastReadIO: IO[Unit]  = _
  var circeReadIO: IO[Unit] = _

  @Setup
  override def setup(): Unit = {
    super.setup()
    fastReadIO = bsonColl.find.first
      // .map(read => assert(read == data, "Bson read != data"))
      .void
    circeReadIO = circeColl.find.first
      // .map(read => assert(read == data, "Circe read != data"))
      .void

    (for {
      _         <- circeColl.insertOne(data)
      readCirce <- circeColl.find.first
      readBson  <- bsonColl.find.first
      _ = assert(readCirce == data.some)
      _ = assert(readBson == data.some)
    } yield ()).unsafeRunSync()(IORuntime.global)
  }

  @Benchmark
  def a_mongoDbToAdtViaDerivationRead(): Unit =
    fastReadIO.unsafeRunSync()(IORuntime.global)

  @Benchmark
  def b_mongoDbToAdtViaCirceRead(): Unit =
    circeReadIO.unsafeRunSync()(IORuntime.global)
}
