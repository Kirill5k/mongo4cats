package mongo4cats.database

import cats.effect.IO
import mongo4cats.EmbeddedMongo
import mongo4cats.client.MongoClientF
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import mongo4cats.database.codecs._
import org.mongodb.scala.bson.Document

import scala.concurrent.ExecutionContext

class MongoCollectionFSpec extends AnyWordSpec with Matchers with EmbeddedMongo {

  implicit val cs = IO.contextShift(ExecutionContext.global)

  "A MongoCollectionF" should {

    "store new document in db" in {
      withEmbeddedMongoDatabase { db =>
        val result = for {
          coll <- db.getCollection[Document]("coll")
          insertResult <- coll.insertOne[IO](document())
          documents <- coll.find.all[IO]()
        } yield (insertResult, documents)

        val (insertRes, documents) = result.unsafeRunSync()

        documents must have size 1
        documents.head.getString("name") must be ("test-doc-1")
        insertRes.wasAcknowledged() must be (true)
      }
    }
  }

  def withEmbeddedMongoDatabase[A](test: MongoDatabaseF[IO] => A): A =
    withRunningEmbeddedMongo() {
      MongoClientF
        .fromConnectionString[IO]("mongodb://localhost:12345")
        .use { client =>
          client.getDatabase("db").flatMap(db => IO(test(db)))
        }
        .unsafeRunSync()
    }

  def document(name: String = "test-doc-1"): Document =
    Document("name" -> name, "type" -> "database", "count" -> 1, "info" -> Document("x" -> 203, "y" -> 102))
}
