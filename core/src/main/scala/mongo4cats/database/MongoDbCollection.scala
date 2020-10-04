package mongo4cats.database

import cats.effect.{Async, Sync}
import mongo4cats.errors.{FindError, InsertionError}
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.{MongoCollection, Observer}
import org.mongodb.scala.result.InsertOneResult
import org.mongodb.scala.model.Filters._

final case class DocumentId(value: String) extends AnyVal

final class MongoDbCollection[F[_]: Async, T] private(
    private val collection: MongoCollection[Document]
) {

  def insertOne(item: T): F[DocumentId] =
    Async[F].async { k =>
      collection.insertOne(toDocument(item)).subscribe(new Observer[InsertOneResult] {
        private var res: InsertOneResult = _

        override def onNext(result: InsertOneResult): Unit =
          res = result

        override def onError(e: Throwable): Unit =
          k(Left(InsertionError(e.getMessage)))

        override def onComplete(): Unit =
          k(Right(DocumentId(res.getInsertedId.asString().getValue)))
      })
    }

  def findOne(id: DocumentId): F[T] =
   Async[F].async { k =>
     collection.find(equal("_id", id.value)).first().subscribe(new Observer[Document] {
       private var res: Document = _

       override def onNext(result: Document): Unit =
        res = result

       override def onError(e: Throwable): Unit =
         k(Left(FindError(e.getMessage)))

       override def onComplete(): Unit =
         k(Right(fromDocument(res)))
     })
   }

  private def toDocument(t: T): Document = ???
  private def fromDocument(document: Document): T = ???
}

object MongoDbCollection {

  def make[F[_]: Async, T](collection: MongoCollection[Document]): F[MongoDbCollection[F, T]] =
    Sync[F].delay(new MongoDbCollection[F, T](collection))
}
