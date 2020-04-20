package mongo4cats.client

import cats.effect.{Resource, Sync}
import cats.implicits._
import org.mongodb.scala.connection.ClusterSettings
import org.mongodb.scala.{MongoClient, MongoClientSettings, ServerAddress}

final case class MongoServerAddress(host: String, port: Int)

import scala.jdk.CollectionConverters._

class MongoDbClient[F[_]: Sync] private (client: MongoClient) {
  def close(): F[Unit] =
    Sync[F].delay(client.close())
}

object MongoDbClient {
  def mongoDbClient[F[_]: Sync](serverAddresses: MongoServerAddress*): Resource[F, MongoDbClient[F]] = {
    val servers = serverAddresses.map(s => new ServerAddress(s.host, s.port)).toList.asJava
    val settings = MongoClientSettings
      .builder()
      .applyToClusterSettings((builder: ClusterSettings.Builder) => builder.hosts(servers))
      .build()
    clientResource(MongoClient(settings))
  }

  def mongoDbClient[F[_]: Sync](connectionString: String): Resource[F, MongoDbClient[F]] =
    clientResource(MongoClient(connectionString))

  private def clientResource[F[_]: Sync](client: => MongoClient): Resource[F, MongoDbClient[F]] =
    Resource.make(Sync[F].delay(client).map(c => new MongoDbClient[F](c)))(_.close())
}
