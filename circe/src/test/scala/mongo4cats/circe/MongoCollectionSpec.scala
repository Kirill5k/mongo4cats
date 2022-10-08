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

package mongo4cats.circe

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import io.circe.generic.auto._
import io.circe.{Decoder, Encoder}
import mongo4cats.bson.ObjectId
import mongo4cats.client.MongoClient
import mongo4cats.operations.Filter
import mongo4cats.embedded.EmbeddedMongo
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import java.time.temporal.ChronoField.MILLI_OF_SECOND
import java.time.temporal.ChronoUnit
import java.time.{Instant, LocalDate}
import scala.concurrent.Future

class MongoCollectionSpec extends AsyncWordSpec with Matchers with EmbeddedMongo {

  override val mongoPort: Int = 12348

  "A MongoCollection" should {

    abstract class Gender(val value: String)
    object Gender {
      case object Male   extends Gender("male")
      case object Female extends Gender("female")

      val all = List(Male, Female)

      def from(value: String): Either[String, Gender] =
        all.find(_.value == value).toRight(s"unexpected item kind $value")

      implicit val decode: Decoder[Gender] = Decoder[String].emap(Gender.from)
      implicit val encode: Encoder[Gender] = Encoder[String].contramap(_.value)
    }

    final case class Address(
        streetNumber: Int,
        streetName: String,
        city: String,
        postcode: String
    )

    final case class Person(
        _id: ObjectId,
        gender: Gender,
        firstName: String,
        lastName: String,
        aliases: List[String],
        dob: LocalDate,
        address: Address,
        registrationDate: Instant
    )

    "use circe codecs for encoding and decoding data" in {
      withEmbeddedMongoClient { client =>
        val p = person()
        val result = for {
          db   <- client.getDatabase("test")
          _    <- db.createCollection("people")
          coll <- db.getCollectionWithCodec[Person]("people")
          _    <- coll.insertOne(p)
          filter = Filter.lt("dob", LocalDate.now()) && Filter.lt("registrationDate", Instant.now())
          people <- coll.find(filter).all
        } yield people

        result.map(_ mustBe List(p))
      }
    }

    "use circe-codec-provider for encoding and decoding data" in {
      withEmbeddedMongoClient { client =>
        val p = person()
        val result = for {
          db     <- client.getDatabase("test")
          _      <- db.createCollection("people")
          coll   <- db.getCollectionWithCodec[Person]("people")
          _      <- coll.insertOne(p)
          people <- coll.find.all
        } yield people

        result.map(_ mustBe List(p))
      }
    }

    "find distinct nested objects" in {
      withEmbeddedMongoClient { client =>
        val result = for {
          db        <- client.getDatabase("test")
          _         <- db.createCollection("people")
          coll      <- db.getCollectionWithCodec[Person]("people")
          _         <- coll.insertMany(List(person("John", "Bloggs"), person("John", "Doe"), person("John", "Smith")))
          addresses <- coll.withAddedCodec[Address].distinct[Address]("address").all
        } yield addresses

        result.map { res =>
          res mustBe List(Address(611, "5th Ave", "New York", "NY 10022"))
        }
      }
    }

    "find by items in array" in {
      withEmbeddedMongoClient { client =>
        val newPerson = person()
        val result = for {
          db   <- client.getDatabase("test")
          _    <- db.createCollection("people")
          coll <- db.getCollectionWithCodec[Person]("people")
          _    <- coll.insertOne(newPerson)
          res  <- coll.find(Filter.in("aliases", List("Bob"))).all
        } yield res

        result.map { res =>
          res mustBe List(newPerson)
        }
      }
    }

    "find distinct nested objects via distinctWithCode" in {
      withEmbeddedMongoClient { client =>
        val result = for {
          db        <- client.getDatabase("test")
          _         <- db.createCollection("people")
          coll      <- db.getCollectionWithCodec[Person]("people")
          _         <- coll.insertMany(List(person("John", "Bloggs"), person("John", "Doe"), person("John", "Smith")))
          addresses <- coll.distinctWithCodec[Address]("address").all
        } yield addresses

        result.map { res =>
          res mustBe List(Address(611, "5th Ave", "New York", "NY 10022"))
        }
      }
    }

    "find distinct nested enums" in {
      withEmbeddedMongoClient { client =>
        val result = for {
          db      <- client.getDatabase("test")
          _       <- db.createCollection("people")
          coll    <- db.getCollectionWithCodec[Person]("people")
          _       <- coll.insertMany(List(person("Jane", "Doe", Gender.Female), person("John", "Smith")))
          genders <- coll.distinctWithCodec[Gender]("gender").all
        } yield genders

        result.map { res =>
          res.toSet mustBe Set(Gender.Female, Gender.Male)
        }
      }
    }

    "search by nested classes" in {
      withEmbeddedMongoClient { client =>
        val result = for {
          db      <- client.getDatabase("test")
          _       <- db.createCollection("people")
          coll    <- db.getCollectionWithCodec[Person]("people")
          _       <- coll.insertMany(List(person("John", "Doe"), person("Jane", "Doe", Gender.Female)))
          females <- coll.withAddedCodec[Gender].find.filter(Filter.eq("gender", Gender.Female)).all
        } yield females

        result.map { res =>
          res must have size 1
        }
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
      val p1 = Payment(ObjectId(), BigDecimal(10), Paypal("foo@bar.com"), ts.plus(1, ChronoUnit.DAYS))
      val p2 = Payment(ObjectId(), BigDecimal(25), CreditCard("John Bloggs", "1234", "1021", 123), ts.plus(2, ChronoUnit.DAYS))

      withEmbeddedMongoClient { client =>
        val result = for {
          db       <- client.getDatabase("test")
          _        <- db.createCollection("payments")
          coll     <- db.getCollectionWithCodec[Payment]("payments")
          _        <- coll.insertMany(List(p1, p2))
          payments <- coll.find.filter(Filter.gt("date", ts)).all
        } yield payments

        result.map(_ mustBe List(p1, p2))
      }
    }

    def person(firstName: String = "John", lastName: String = "Bloggs", gender: Gender = Gender.Male): Person =
      Person(
        ObjectId(),
        gender,
        firstName,
        lastName,
        List("Bob", "Doe"),
        LocalDate.parse("1970-12-01"),
        Address(611, "5th Ave", "New York", "NY 10022"),
        Instant.now().`with`(MILLI_OF_SECOND, 0)
      )
  }

  def withEmbeddedMongoClient[A](test: MongoClient[IO] => IO[A]): Future[A] =
    withRunningEmbeddedMongo {
      MongoClient
        .fromConnectionString[IO](s"mongodb://localhost:$mongoPort")
        .use(test)
    }.unsafeToFuture()(IORuntime.global)
}
