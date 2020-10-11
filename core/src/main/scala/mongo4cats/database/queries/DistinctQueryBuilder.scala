package mongo4cats.database.queries

import cats.effect.{Async, Concurrent, Sync}
import com.mongodb.client.model
import fs2.concurrent.Queue
import mongo4cats.database.helpers.{multipleItemsObserver, singleItemObserver, streamObserver}
import org.bson.conversions.Bson
import org.mongodb.scala.DistinctObservable

import scala.reflect.ClassTag

final class DistinctQueryBuilder[T: reflect.ClassTag] private (
    protected val observable: DistinctObservable[T],
    protected val commands: List[DistinctCommand[T]]
) extends QueryBuilder[DistinctObservable, T] {

  def filter(filter: Bson): DistinctQueryBuilder[T] =
    DistinctQueryBuilder[T](observable, DistinctCommand.Filter[T](filter) :: commands)

  def batchSize(size: Int): DistinctQueryBuilder[T] =
    DistinctQueryBuilder[T](observable, DistinctCommand.BatchSize[T](size) :: commands)

  def collation(collation: model.Collation): DistinctQueryBuilder[T] =
    DistinctQueryBuilder[T](observable, DistinctCommand.Collation[T](collation) :: commands)

  def first[F[_]: Async]: F[T] =
    Async[F].async { k =>
      applyCommands().first().subscribe(singleItemObserver(k))
    }

  def all[F[_]: Async]: F[Iterable[T]] =
    Async[F].async { k =>
      applyCommands().subscribe(multipleItemsObserver[T](k))
    }

  def stream[F[_]: Concurrent]: fs2.Stream[F, T] =
    for {
      q   <- fs2.Stream.eval(Queue.noneTerminated[F, T])
      _   <- fs2.Stream.eval(Sync[F].delay(applyCommands().subscribe(streamObserver[F, T](q))))
      doc <- q.dequeue
    } yield doc
}

object DistinctQueryBuilder {
  def apply[T: ClassTag](
      observable: DistinctObservable[T],
      commands: List[DistinctCommand[T]] = Nil
  ): DistinctQueryBuilder[T] = new DistinctQueryBuilder(observable, commands)
}
