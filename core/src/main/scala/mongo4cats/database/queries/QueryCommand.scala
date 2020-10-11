package mongo4cats.database.queries

import com.mongodb.client.model
import org.bson.conversions.Bson
import org.mongodb.scala.{DistinctObservable, FindObservable, Observable}

private[queries] sealed trait QueryCommand[O[_] <: Observable[_], T] {
  def run(observable: O[T]): O[T]
}

private[queries] sealed trait DistinctCommand[T] extends QueryCommand[DistinctObservable, T]
private[queries] sealed trait FindCommand[T] extends QueryCommand[FindObservable, T]

private[queries] object FindCommand {
  final case class Limit[T](n: Int) extends FindCommand[T] {
    override def run(observable: FindObservable[T]): FindObservable[T] =
      observable.limit(n)
  }

  final case class Sort[T](order: Bson) extends FindCommand[T] {
    override def run(observable: FindObservable[T]): FindObservable[T] =
      observable.sort(order)
  }

  final case class Filter[T](filter: Bson) extends FindCommand[T] {
    override def run(observable: FindObservable[T]): FindObservable[T] =
      observable.filter(filter)
  }

  final case class Projection[T](projection: Bson) extends FindCommand[T] {
    override def run(observable: FindObservable[T]): FindObservable[T] =
      observable.projection(projection)
  }
}

private[queries] object DistinctCommand {

  final case class Filter[T](filter: Bson) extends DistinctCommand[T] {
    override def run(observable: DistinctObservable[T]): DistinctObservable[T] =
      observable.filter(filter)
  }

  final case class BatchSize[T](size: Int) extends DistinctCommand[T] {
    override def run(observable: DistinctObservable[T]): DistinctObservable[T] =
      observable.batchSize(size)
  }

  final case class Collation[T](collation: model.Collation) extends DistinctCommand[T] {
    override def run(observable: DistinctObservable[T]): DistinctObservable[T] =
      observable.collation(collation)
  }
}

