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
import cats.effect.unsafe.implicits.global
import com.mongodb.client.model.Filters
import io.circe.generic.auto._
import mongo4cats.EmbeddedMongo
import mongo4cats.circe._
import mongo4cats.client.MongoClientF
import org.bson.types.ObjectId
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.{Instant, LocalDate}
import java.time.temporal.ChronoField.MILLI_OF_SECOND
import java.time.temporal.ChronoUnit

class MongoDatabaseFSpec extends AnyWordSpec with Matchers with EmbeddedMongo {

  final case class Address(streetNumber: Int, streetName: String, city: String, postcode: String)
  final case class Person(
      _id: ObjectId,
      firstName: String,
      lastName: String,
      dob: LocalDate,
      address: Address,
      registrationDate: Instant
  )

  "A MongoDatabaseF" should {

    "use circe codecs for encoding and decoding data" in {
      withEmbeddedMongoClient { client =>
        val p = person()
        val result = for {
          db     <- client.getDatabase("test")
          _      <- db.createCollection("people")
          coll   <- db.getCollectionWithCirceCodecs[Person]("people")
          _      <- coll.insertOne[IO](p)
          people <- coll.find.all[IO]
        } yield people

        result.map(_ mustBe List(p))
      }
    }

    "use circe codecs for encoding and decoding data" in {
      withEmbeddedMongoClient { client =>
        val p = person()
        val result = for {
          db     <- client.getDatabase("test")
          _      <- db.createCollection("people")
          coll   <- db.getCollectionWithCirceCodecs[Person]("people")
          _      <- coll.insertOne[IO](p)
          people <- coll.find.all[IO]
        } yield people

        result.map(_ mustBe List(p))
      }
    }

    sealed trait PaymentMethod
    final case class CreditCard(name: String, number: String, expiry: String, cvv: Int) extends PaymentMethod
    final case class Paypal(email: String)                                              extends PaymentMethod

    final case class Payment(
        id: ObjectId,
        amount: BigDecimal,
        method: PaymentMethod,
        date: Instant
    )

    "encode and decode case classes that extend sealed traits" in {
      val ts = Instant.parse("2020-01-01T00:00:00Z")
      val p1 = Payment(new ObjectId(), BigDecimal(10), Paypal("foo@bar.com"), ts.plus(1, ChronoUnit.DAYS))
      val p2 = Payment(new ObjectId(), BigDecimal(25), CreditCard("John Bloggs", "1234", "1021", 123), ts.plus(2, ChronoUnit.DAYS))

      withEmbeddedMongoClient { client =>
        val result = for {
          db       <- client.getDatabase("test")
          _        <- db.createCollection("payments")
          coll     <- db.getCollectionWithCirceCodecs[Payment]("payments")
          _        <- coll.insertMany[IO](List(p1, p2))
          payments <- coll.find.filter(Filters.gt("date", ts)).all[IO]
        } yield payments

        result.map(_ mustBe List(p1, p2))
      }
    }
  }

  def person(firstName: String = "John", lastName: String = "Bloggs"): Person =
    Person(
      new ObjectId(),
      firstName,
      lastName,
      LocalDate.parse("1970-12-01"),
      Address(611, "5th Ave", "New York", "NY 10022"),
      Instant.now().`with`(MILLI_OF_SECOND, 0)
    )

  def withEmbeddedMongoClient[A](test: MongoClientF[IO] => IO[A]): A =
    withRunningEmbeddedMongo(port = 12347) {
      MongoClientF
        .fromConnectionString[IO]("mongodb://localhost:12347")
        .use(test)
        .unsafeRunSync()
    }
}
