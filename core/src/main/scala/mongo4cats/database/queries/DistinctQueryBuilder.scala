package mongo4cats.database.queries

import cats.effect.Async
import com.mongodb.client.model
import com.mongodb.reactivestreams.client.DistinctPublisher
import mongo4cats.database.helpers._
import mongo4cats.database.operations
import org.bson.conversions.Bson

import scala.concurrent.duration.Duration
import scala.reflect.ClassTag

final case class DistinctQueryBuilder[T: ClassTag] private[database] (
    protected val observable: DistinctPublisher[T],
    protected val commands: List[DistinctCommand[T]]
) extends QueryBuilder[DistinctPublisher, T] {

  def maxTime(duration: Duration): DistinctQueryBuilder[T] =
    DistinctQueryBuilder[T](observable, DistinctCommand.MaxTime[T](duration) :: commands)

  def filter(filter: Bson): DistinctQueryBuilder[T] =
    DistinctQueryBuilder[T](observable, DistinctCommand.Filter[T](filter) :: commands)

  def filter(filters: operations.Filter): DistinctQueryBuilder[T] =
    filter(filters.toBson)

  def batchSize(size: Int): DistinctQueryBuilder[T] =
    DistinctQueryBuilder[T](observable, DistinctCommand.BatchSize[T](size) :: commands)

  def collation(collation: model.Collation): DistinctQueryBuilder[T] =
    DistinctQueryBuilder[T](observable, DistinctCommand.Collation[T](collation) :: commands)

  def first[F[_]: Async]: F[T] =
    applyCommands().first().asyncSingle[F]

  def all[F[_]: Async]: F[Iterable[T]] =
    applyCommands().asyncIterable[F]

  def stream[F[_]: Async]: fs2.Stream[F, T] =
    applyCommands().stream[F]

  def boundedStream[F[_]: Async](capacity: Int): fs2.Stream[F, T] =
    applyCommands().boundedStream[F](capacity)
}
