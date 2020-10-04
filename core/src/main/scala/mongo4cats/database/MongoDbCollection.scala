package mongo4cats.database

import cats.effect.{Async, Sync}
import mongo4cats.errors.InsertionError
import org.mongodb.scala.{MongoCollection, Observer}
import org.mongodb.scala.result.InsertOneResult

final case class DocumentId(value: String) extends AnyVal

final class MongoDbCollection[F[_]: Async, T] private(
    private val collection: MongoCollection[T]
) {


  def insertOne(document: T): F[DocumentId] =
    Async[F].async { k =>
      collection.insertOne(document).subscribe(new Observer[InsertOneResult] {
        private var res: InsertOneResult = _

        override def onNext(result: InsertOneResult): Unit =
          res = result

        override def onError(e: Throwable): Unit =
          k(Left(InsertionError(e.getMessage)))

        override def onComplete(): Unit =
          k(Right(DocumentId(res.getInsertedId.asString().getValue)))
      })
    }
}

object MongoDbCollection {

  def make[F[_]: Async, T](collection: MongoCollection[T]): F[MongoDbCollection[F, T]] =
    Sync[F].delay(new MongoDbCollection[F, T](collection))
}
