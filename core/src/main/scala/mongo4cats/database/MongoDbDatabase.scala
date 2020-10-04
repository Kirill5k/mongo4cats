package mongo4cats.database

import cats.effect.{Async, Sync}
import cats.implicits._
import org.mongodb.scala.{Document, MongoDatabase}
import helpers._
import org.mongodb.scala.model.CreateCollectionOptions

class MongoDbDatabase[F[_]: Async] private(
    private val database: MongoDatabase
) {

  def name: F[String] =
    Sync[F].pure(database.name)

  def getCollection[T](name: String): F[MongoDbCollection[F]] =
    Sync[F]
      .delay(database.getCollection[Document](name))
      .flatMap(MongoDbCollection.make[F])

  def collectionNames(): F[Iterable[String]] =
    Async[F].async { k =>
      database.listCollectionNames().subscribe(multipleItemsObserver[String](k))
    }

  def createCollection(name: String): F[Unit] =
    Async[F].async { k =>
      database.createCollection(name).subscribe(singleItemObserver[Unit](k))
    }
}

object MongoDbDatabase {
  def make[F[_]: Async](database: MongoDatabase): F[MongoDbDatabase[F]] =
    Sync[F].delay(new MongoDbDatabase[F](database))
}
