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

  }

  def withEmbeddedMongoClient[A](test: MongoClientF[IO] => A): A =
    withRunningMongoEmbedded() {
      MongoClientF.fromConnectionString[IO]("mongodb://localhost:12345").use { client =>
        IO(test(client))
      }.unsafeRunSync()
    }
}
