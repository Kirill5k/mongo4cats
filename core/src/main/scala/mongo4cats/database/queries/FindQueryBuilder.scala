package mongo4cats.database.queries

import cats.effect.{Async, Concurrent, Sync}
import fs2.concurrent.Queue
import mongo4cats.database.helpers.{multipleItemsObserver, singleItemObserver, streamObserver}
import org.bson.conversions.Bson
import org.mongodb.scala.FindObservable

import scala.reflect.ClassTag

final class FindQueryBuilder[T: reflect.ClassTag] private(
    protected val observable: FindObservable[T],
    protected val commands: List[FindCommand[T]]
) extends QueryBuilder[FindObservable, T] {

  def sort(sort: Bson): FindQueryBuilder[T] =
    FindQueryBuilder[T](observable, FindCommand.Sort[T](sort) :: commands)

  def filter(filter: Bson): FindQueryBuilder[T] =
    FindQueryBuilder[T](observable, FindCommand.Filter[T](filter) :: commands)

  def projection(projection: Bson): FindQueryBuilder[T] =
    FindQueryBuilder[T](observable, FindCommand.Projection[T](projection) :: commands)

  def limit(limit: Int): FindQueryBuilder[T] =
    FindQueryBuilder[T](observable, FindCommand.Limit[T](limit) :: commands)

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

object FindQueryBuilder {
  def apply[T: ClassTag](
      observable: FindObservable[T],
      commands: List[FindCommand[T]] = Nil
  ): FindQueryBuilder[T] = new FindQueryBuilder(observable, commands)
}
