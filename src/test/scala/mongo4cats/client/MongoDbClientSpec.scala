package mongo4cats.client

import cats.effect.IO
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

class MongoDbClientSpec extends AnyWordSpec with Matchers {

  "A MongoDbClient" should {
    "connect to a db via connection string" in {
      val connectionString = "mongodb://localhost:12345"
      val client = MongoDbClient.mongoDbClient[IO](connectionString)
    }
  }
}
