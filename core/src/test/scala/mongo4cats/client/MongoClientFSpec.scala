package mongo4cats.client

import cats.effect.IO
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

class MongoClientFSpec extends AnyWordSpec with Matchers with MongoEmbedded {

  "A MongoDbClient" should {
    "connect to a db via connection string" in {
      val connectionString = "mongodb://localhost:8080"
      withRunningMongoEmbedded() {
        val result = MongoClientF.fromConnectionString[IO](connectionString)
          .use(_.getDatabase("test"))
          .flatMap(_.name)
          .attempt.unsafeRunSync()
        result must be (Right("test"))
      }
    }

    "connect to a db via server address string" in {
      withRunningMongoEmbedded(port = 54321) {
        val result = MongoClientF.fromServerAddress[IO](MongoServerAddress("localhost", 54321))
          .use(_.getDatabase("foo"))
          .flatMap(_.name)
          .attempt.unsafeRunSync()
        result must be (Right("foo"))
      }
    }
  }
}
