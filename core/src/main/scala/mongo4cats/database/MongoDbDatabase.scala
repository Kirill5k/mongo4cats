package mongo4cats.database

import cats.effect.Sync
import cats.implicits._
import org.mongodb.scala.MongoDatabase

class MongoDbDatabase[F[_]: Sync] private (
    private val database: MongoDatabase
) {

  def name: F[String] =
    Sync[F].pure(database.name)

  def getCollection[T](name: String): F[MongoDbCollection[F, T]] =
    Sync[F]
      .delay(database.getCollection[T](name))
      .flatMap(MongoDbCollection.make)
}

object MongoDbDatabase {
  def make[F[_]: Sync](database: MongoDatabase): F[MongoDbDatabase[F]] =
    Sync[F].delay(new MongoDbDatabase[F](database))
}
