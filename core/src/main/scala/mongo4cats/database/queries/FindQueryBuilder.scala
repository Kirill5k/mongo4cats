package mongo4cats.database.queries

import cats.effect.Async
import mongo4cats.database.helpers.singleItemObserver
import org.bson.conversions.Bson
import org.mongodb.scala.FindObservable

import scala.reflect.ClassTag

final class FindQueryBuilder[T: reflect.ClassTag] private(
    protected val observable: FindObservable[T],
    protected val commands: List[FindCommand[T]]
) extends QueryBuilder[FindObservable, T] {

  def sort(sort: Bson): FindQueryBuilder[T] =
    FindQueryBuilder[T](observable, FindCommand.Sort[T](sort) :: commands)

  def find(filter: Bson): FindQueryBuilder[T] =
    FindQueryBuilder[T](observable, FindCommand.Find[T](filter) :: commands)

  def projection(projection: Bson): FindQueryBuilder[T] =
    FindQueryBuilder[T](observable, FindCommand.Projection[T](projection) :: commands)

  def limit(limit: Int): FindQueryBuilder[T] =
    FindQueryBuilder[T](observable, FindCommand.Limit[T](limit) :: commands)

  def first[F[_]: Async]: F[T] =
    Async[F].async { k =>
      applyCommands().first().subscribe(singleItemObserver(k))
    }
}

object FindQueryBuilder {
  def apply[T: ClassTag](
      observable: FindObservable[T],
      commands: List[FindCommand[T]] = Nil
  ): FindQueryBuilder[T] = new FindQueryBuilder(observable, commands)
}
