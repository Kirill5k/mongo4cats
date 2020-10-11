package mongo4cats.database.queries

import cats.effect.{Async, ConcurrentEffect}
import com.mongodb.client.model
import mongo4cats.database.helpers.{multipleItemsAsync, singleItemAsync, unicastPublisher}
import fs2.interop.reactivestreams._
import org.bson.conversions.Bson
import org.mongodb.scala.{ChangeStreamObservable, DistinctObservable, FindObservable, Observable}

import scala.reflect.ClassTag

private[queries] trait QueryBuilder[O[_] <: Observable[_], T] {
  protected def observable: O[T]
  protected def commands: List[QueryCommand[O, T]]

  protected def applyCommands(): O[T] =
    commands.reverse.foldLeft(observable) {
      case (obs, comm) => comm.run(obs)
    }
}

final class FindQueryBuilder[T: reflect.ClassTag] private (
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
    Async[F].async(singleItemAsync(applyCommands().first()))

  def all[F[_]: Async]: F[Iterable[T]] =
    Async[F].async(multipleItemsAsync(applyCommands()))

  def stream[F[_]: ConcurrentEffect]: fs2.Stream[F, T] =
    unicastPublisher[T](applyCommands()).toStream[F]
}

object FindQueryBuilder {
  def apply[T: ClassTag](
      observable: FindObservable[T],
      commands: List[FindCommand[T]] = Nil
  ): FindQueryBuilder[T] = new FindQueryBuilder(observable, commands)
}

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
    Async[F].async(singleItemAsync(applyCommands().first()))

  def all[F[_]: Async]: F[Iterable[T]] =
    Async[F].async(multipleItemsAsync(applyCommands()))

  def stream[F[_]: ConcurrentEffect]: fs2.Stream[F, T] =
    unicastPublisher[T](applyCommands()).toStream[F]
}

object DistinctQueryBuilder {
  def apply[T: ClassTag](
      observable: DistinctObservable[T],
      commands: List[DistinctCommand[T]] = Nil
  ): DistinctQueryBuilder[T] = new DistinctQueryBuilder(observable, commands)
}

final class WatchQueryBuilder[T: reflect.ClassTag] private (
    protected val observable: ChangeStreamObservable[T],
    protected val commands: List[WatchCommand[T]]
) extends QueryBuilder[ChangeStreamObservable, T] {}

object WatchQueryBuilder {
  def apply[T: ClassTag](
      observable: ChangeStreamObservable[T],
      commands: List[WatchCommand[T]] = Nil
  ): WatchQueryBuilder[T] = new WatchQueryBuilder(observable, commands)
}
