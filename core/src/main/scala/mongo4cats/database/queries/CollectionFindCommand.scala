package mongo4cats.database.queries

import org.bson.conversions.Bson
import org.mongodb.scala.FindObservable

private[queries] sealed trait CollectionFindCommand[T] {
  def run(observable: FindObservable[T]): FindObservable[T]
}

private[queries] object CollectionFindCommand {

  final case class Limit[T](n: Int) extends CollectionFindCommand[T] {
    override def run(observable: FindObservable[T]): FindObservable[T] =
      observable.limit(n)
  }

  final case class Sort[T](order: Bson) extends CollectionFindCommand[T] {
    override def run(observable: FindObservable[T]): FindObservable[T] =
      observable.sort(order)
  }

  final case class Find[T](filter: Bson) extends CollectionFindCommand[T] {
    override def run(observable: FindObservable[T]): FindObservable[T] =
      observable.filter(filter)
  }

  final case class Projection[T](projection: Bson) extends CollectionFindCommand[T] {
    override def run(observable: FindObservable[T]): FindObservable[T] =
      observable.projection(projection)
  }
}
