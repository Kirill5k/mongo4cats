package mongo4cats.client

import cats.effect.{Concurrent, Resource, Sync}
import cats.implicits._
import mongo4cats.database.MongoDatabaseF
import org.mongodb.scala.{MongoClient, MongoClientSettings, ServerAddress}

import scala.jdk.CollectionConverters._

final class MongoClientF[F[_]: Concurrent] private(
    private val client: MongoClient
) {

  def getDatabase(name: String): F[MongoDatabaseF[F]] =
    Sync[F]
      .delay(client.getDatabase(name))
      .flatMap(MongoDatabaseF.make[F])
}

object MongoClientF {
  def fromServerAddress[F[_]: Concurrent](serverAddresses: ServerAddress*): Resource[F, MongoClientF[F]] = {
    val settings = MongoClientSettings
      .builder()
      .applyToClusterSettings { builder =>
        val _ = builder.hosts(serverAddresses.toList.asJava)
      }
      .build()
    clientResource(MongoClient(settings))
  }

  def fromConnectionString[F[_]: Concurrent](connectionString: String): Resource[F, MongoClientF[F]] =
    clientResource(MongoClient(connectionString))

  private def clientResource[F[_]: Concurrent](client: => MongoClient): Resource[F, MongoClientF[F]] =
    Resource.fromAutoCloseable(Sync[F].delay(client)).map(c => new MongoClientF[F](c))
}
