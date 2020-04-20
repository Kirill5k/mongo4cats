package mongo4cats

import cats.effect.{IO, Resource}
import mongo4cats.client.{MongoDbClient, MongoServerAddress}
import org.mongodb.scala.connection.ClusterSettings
import org.mongodb.scala.{Document, MongoClient, MongoClientSettings, MongoCollection, MongoDatabase, ServerAddress}

class Application extends App {

  val client: Resource[IO, MongoDbClient[IO]] =
    MongoDbClient.mongoDbClient(MongoServerAddress("localhost", 27017))

}
