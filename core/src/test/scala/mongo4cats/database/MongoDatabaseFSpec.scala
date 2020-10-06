package mongo4cats.database

import cats.effect.IO
import mongo4cats.MongoEmbedded
import mongo4cats.client.MongoClientF
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.ExecutionContext

class MongoDatabaseFSpec extends AnyWordSpec with Matchers with MongoEmbedded {

  implicit val cs = IO.contextShift(ExecutionContext.global)

  "A MongoDatabaseF" should {

    "create new collection" in {
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
  }

  def withEmbeddedMongoClient[A](test: MongoClientF[IO] => A): A =
    withRunningMongoEmbedded() {
      MongoClientF
        .fromConnectionString[IO]("mongodb://localhost:12345")
        .use { client =>
          IO(test(client))
        }
        .unsafeRunSync()
    }
}
