package mongo4cats.client

import cats.effect.IO
import cats.implicits._
import mongo4cats.MongoEmbedded
import org.mongodb.scala.ServerAddress
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.ExecutionContext

class MongoClientFSpec extends AnyWordSpec with Matchers with MongoEmbedded {

  implicit val cs = IO.contextShift(ExecutionContext.global)

  "A MongoDbClient" should {
    "connect to a db via connection string" in {
      withRunningMongoEmbedded() {
        val result = MongoClientF.fromConnectionString[IO]("mongodb://localhost:12345").use { client =>
          for {
            db <- client.getDatabase("test-db")
            names <- db.collectionNames()
          } yield names
        }.attempt.unsafeRunSync()

        result must be (Right(Nil))
      }
    }

    "connect to a db via server address string" in {
      withRunningMongoEmbedded() {
        val server = new ServerAddress("localhost", 12345)
        val result = MongoClientF.fromServerAddress[IO](server).use { client =>
          for {
            db <- client.getDatabase("test-db")
            names <- db.collectionNames()
          } yield names
        }.attempt.unsafeRunSync()

        result must be (Right(Nil))
      }
    }

    "return error when port is invalid" in {
      withRunningMongoEmbedded() {
        val server = new ServerAddress("localhost", 123)
        val result = MongoClientF.fromServerAddress[IO](server).use { client =>
          for {
            db <- client.getDatabase("test-db")
            names <- db.collectionNames()
          } yield names
        }.attempt.unsafeRunSync()

        result.isLeft must be (true)
        result.leftMap(_.getMessage) must be (Left("Timed out after 30000 ms while waiting for a server that matches ReadPreferenceServerSelector{readPreference=primary}. Client view of cluster state is {type=UNKNOWN, servers=[{address=localhost:123, type=UNKNOWN, state=CONNECTING, exception={com.mongodb.MongoSocketOpenException: Exception opening socket}, caused by {java.net.ConnectException: Connection refused}}]"))
      }
    }
  }
}
