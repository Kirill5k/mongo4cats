package mongo4cats.database

import cats.effect.IO
import mongo4cats.EmbeddedMongo
import mongo4cats.client.MongoClientF
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.ExecutionContext

class MongoDatabaseFSpec extends AnyWordSpec with Matchers with EmbeddedMongo {

  implicit val cs = IO.contextShift(ExecutionContext.global)

  "A MongoDatabaseF" should {

    "create new collections and return collection names" in {
      withEmbeddedMongoClient { client =>
        val result = for {
          db    <- client.getDatabase("foo")
          _     <- db.createCollection("c1")
          _     <- db.createCollection("c2")
          names <- db.collectionNames()
        } yield names

        result.unsafeRunSync() must be(List("c2", "c1"))
      }
    }

    "return collection by name" in {
      withEmbeddedMongoClient { client =>
        val result = for {
          db         <- client.getDatabase("foo")
          _          <- db.createCollection("c1")
          collection <- db.getCollection("c1")
        } yield collection.namespace

        val namespace = result.unsafeRunSync()

        namespace.getDatabaseName must be("foo")
        namespace.getCollectionName must be("c1")
      }
    }
  }

  def withEmbeddedMongoClient[A](test: MongoClientF[IO] => A): A =
    withRunningEmbeddedMongo() {
      MongoClientF
        .fromConnectionString[IO]("mongodb://localhost:12345")
        .use { client =>
          IO(test(client))
        }
        .unsafeRunSync()
    }
}
