package mongo4cats

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import io.circe.generic.auto._
import mongo4cats.circe._
import mongo4cats.client.MongoClientF
import org.mongodb.scala.bson.ObjectId
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

class MongoDatabaseFSpec extends AnyWordSpec with Matchers with EmbeddedMongo {

  implicit val runTime = IORuntime.global

  "A MongoDatabaseF" should {

    final case class Address(streetNumber: Int, streetName: String, city: String, postcode: String)
    final case class Person(_id: ObjectId, firstName: String, lastName: String, address: Address)

    "use circe codecs for encoding and decoding data" in {
      val person = Person(
        new ObjectId(),
        "John",
        "Bloggs",
        Address(611, "5th Ave", "New York", "NY 10022")
      )

      withEmbeddedMongoClient { client =>
        val result = for {
          db     <- client.getDatabase("test")
          _      <- db.createCollection("people")
          coll   <- db.getCollectionWithCirceCodecs[Person]("people")
          _      <- coll.insertOne[IO](person)
          people <- coll.find.all[IO]
        } yield people

        result.map(_ mustBe List(person))
      }
    }
  }

  def withEmbeddedMongoClient[A](test: MongoClientF[IO] => IO[A]): A =
    withRunningEmbeddedMongo() {
      MongoClientF
        .fromConnectionString[IO]("mongodb://localhost:12345")
        .use(test)
        .unsafeRunSync()
    }
}
