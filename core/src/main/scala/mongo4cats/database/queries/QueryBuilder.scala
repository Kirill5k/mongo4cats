package mongo4cats.database.queries

import cats.effect.{Async, Concurrent, Sync}
import fs2.concurrent.Queue
import mongo4cats.database.helpers.{multipleItemsObserver, singleItemObserver, streamObserver}
import org.bson.conversions.Bson
import org.mongodb.scala.{FindObservable}

import scala.reflect.ClassTag

final class QueryBuilder[T: reflect.ClassTag] private (
    private val observable: FindObservable[T],
    private val commands: List[CollectionFindCommand[T]]
) {

  def sort(sort: Bson): QueryBuilder[T] =
    QueryBuilder[T](observable, CollectionFindCommand.Sort[T](sort) :: commands)

  def find(filter: Bson): QueryBuilder[T] =
    QueryBuilder[T](observable, CollectionFindCommand.Find[T](filter) :: commands)

  def projection(projection: Bson): QueryBuilder[T] =
    QueryBuilder[T](observable, CollectionFindCommand.Projection[T](projection) :: commands)

  def limit(limit: Int): QueryBuilder[T] =
    QueryBuilder[T](observable, CollectionFindCommand.Limit[T](limit) :: commands)

  def first[F[_]: Async](): F[T] =
    Async[F].async { k =>
      applyCommands().first().subscribe(singleItemObserver(k))
    }

  def all[F[_]: Async](): F[Iterable[T]] =
    Async[F].async { k =>
      applyCommands().subscribe(multipleItemsObserver(k))
    }

  def stream[F[_]: Concurrent](): fs2.Stream[F, T] =
    for {
      q   <- fs2.Stream.eval(Queue.noneTerminated[F, T])
      _   <- fs2.Stream.eval(Sync[F].delay(applyCommands().subscribe(streamObserver(q))))
      doc <- q.dequeue
    } yield doc

  private def applyCommands(): FindObservable[T] =
    commands.reverse.foldLeft(observable) {
      case (obs, comm) => comm.run(obs)
    }
}

object QueryBuilder {
  def apply[T: ClassTag](
      observable: FindObservable[T],
      commands: List[CollectionFindCommand[T]] = Nil
  ): QueryBuilder[T] = new QueryBuilder(observable, commands)
}
