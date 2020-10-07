package mongo4cats.database

import cats.effect.IO
import mongo4cats.EmbeddedMongo
import mongo4cats.client.MongoClientF
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.ExecutionContext

class MongoCollectionFSpec extends AnyWordSpec with Matchers with EmbeddedMongo {

  implicit val cs = IO.contextShift(ExecutionContext.global)

  "A MongoCollectionF" should {

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
}
