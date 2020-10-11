package mongo4cats.database.queries

import cats.effect.Async
import com.mongodb.client.model
import mongo4cats.database.helpers.singleItemObserver
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
}

object DistinctQueryBuilder {
  def apply[T: ClassTag](
      observable: DistinctObservable[T],
      commands: List[DistinctCommand[T]] = Nil
  ): DistinctQueryBuilder[T] = new DistinctQueryBuilder(observable, commands)
}
