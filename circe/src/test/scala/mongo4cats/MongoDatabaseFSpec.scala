/*
 * Copyright 2020 Kirill5k
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package mongo4cats

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import io.circe.generic.auto._
import mongo4cats.EmbeddedMongo
import mongo4cats.circe._
import mongo4cats.client.MongoClientF
import org.mongodb.scala.bson.ObjectId
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.Instant
import java.time.temporal.ChronoField.MILLI_OF_SECOND

class MongoDatabaseFSpec extends AnyWordSpec with Matchers with EmbeddedMongo {

  implicit val runTime = IORuntime.global

  "A MongoDatabaseF" should {

    final case class Address(streetNumber: Int, streetName: String, city: String, postcode: String)
    final case class Person(_id: ObjectId, firstName: String, lastName: String, address: Address, registrationDate: Instant)

    "use circe codecs for encoding and decoding data" in {
      val person = Person(
        new ObjectId(),
        "John",
        "Bloggs",
        Address(611, "5th Ave", "New York", "NY 10022"),
        Instant.now().`with`(MILLI_OF_SECOND, 0)
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
    withRunningEmbeddedMongo(port = 12347) {
      MongoClientF
        .fromConnectionString[IO]("mongodb://localhost:12347")
        .use(test)
        .unsafeRunSync()
    }
}
