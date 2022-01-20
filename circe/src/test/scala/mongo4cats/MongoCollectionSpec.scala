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
import io.circe.{Decoder, Encoder}
import io.circe.generic.auto._
import mongo4cats.circe.implicits._
import mongo4cats.circe.unsafe
import mongo4cats.client.MongoClient
import mongo4cats.collection.operations.Filter
import mongo4cats.embedded.EmbeddedMongo
import mongo4cats.bson.ObjectId
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import java.time.{Instant, LocalDate}
import java.time.temporal.ChronoField.MILLI_OF_SECOND
import java.time.temporal.ChronoUnit
import scala.concurrent.Future

class MongoCollectionSpec extends AsyncWordSpec with Matchers with EmbeddedMongo {

  import MongoCollectionSpec._

  implicit val personEnc = unsafe.circeDocumentEncoder[Person]
  implicit val paymentEnc = unsafe.circeDocumentEncoder[Payment]

  override val mongoPort: Int = 12348

  "A MongoCollection" should {
    "use circe codecs for encoding and decoding data" in {
      withEmbeddedMongoClient { client =>
        val p = person()
        val result = for {
          db <- client.getDatabase("test")
          _ <- db.createCollection("people")
          coll <- db.getCollection("people")
          _ <- coll.insertOne[Person](p)
          filter = Filter.lt("dob", LocalDate.now()) && Filter.lt(
            "registrationDate",
            Instant.now()
          )
          people <- coll.find.stream[Person].compile.to(List)
        } yield people

        result.map(_ mustBe List(p))
      }
    }

    "find distinct nested objects" in {
      withEmbeddedMongoClient { client =>
        val result = for {
          db <- client.getDatabase("test")
          _ <- db.createCollection("people")
          coll <- db.getCollection("people")
          _ <- coll.insertMany[Person](
            List(person("John", "Bloggs"), person("John", "Doe"), person("John", "Smith"))
          )
          addresses <- coll.distinct("address").stream[Address].compile.to(List)
        } yield addresses

        result.map { res =>
          res mustBe List(Address(611, "5th Ave", "New York", "NY 10022"))
        }
      }
    }

    "find distinct nested enums" in {
      withEmbeddedMongoClient { client =>
        val result = for {
          db <- client.getDatabase("test")
          _ <- db.createCollection("people")
          coll <- db.getCollection("people")
          _ <- coll.insertMany[Person](
            List(person("Jane", "Doe", Gender.Female), person("John", "Smith"))
          )
          genders <- coll.distinct("gender").stream[Gender].compile.to(List)
        } yield genders

        result.map { res =>
          res.toSet mustBe Set(Gender.Female, Gender.Male)
        }
      }
    }

    "search by nested classes" in {
      import io.circe.syntax._
      withEmbeddedMongoClient { client =>
        val result = for {
          db <- client.getDatabase("test")
          _ <- db.createCollection("people")
          coll <- db.getCollection("people")
          _ <- coll.insertMany[Person](
            List(person("John", "Doe"), person("Jane", "Doe", Gender.Female))
          )
          all <- coll.find.stream[Person].compile.to(List)
          females <- coll
            // w/o auto derive codecs, this type annotation is redundant (idk, what it finds, but it encodes
            // Gender.Female to {})
            .find
            .filter(Filter.eq[Gender]("gender", Gender.Female))
            .stream[Person]
            .compile
            .to(List)
        } yield females

        result.map { res =>
          res must have size 1
        }
      }
    }

    "encode and decode case classes that extend sealed traits" in {
      val ts = Instant.parse("2020-01-01T00:00:00Z")
      val p1 =
        Payment(ObjectId(), BigDecimal(10), Paypal("foo@bar.com"), ts.plus(1, ChronoUnit.DAYS))
      val p2 = Payment(
        ObjectId(),
        BigDecimal(25),
        CreditCard("John Bloggs", "1234", "1021", 123),
        ts.plus(2, ChronoUnit.DAYS)
      )

      withEmbeddedMongoClient { client =>
        val result = for {
          db <- client.getDatabase("test")
          _ <- db.createCollection("payments")
          coll <- db.getCollection("payments")
          _ <- coll.insertMany[Payment](List(p1, p2))
          payments <- coll.find
            .filter(Filter.gt("date", ts))
            .stream[Payment]
            .compile
            .to(List)
        } yield payments

        result.map(_ mustBe List(p1, p2))
      }
    }
  }
  def withEmbeddedMongoClient[A](test: MongoClient[IO] => IO[A]): Future[A] =
    withRunningEmbeddedMongo {
      MongoClient
        .fromConnectionString[IO](s"mongodb://localhost:$mongoPort")
        .use(test)
    }.unsafeToFuture()
}

object MongoCollectionSpec {
  abstract class Gender(val value: String)

  object Gender {
    case object Male extends Gender("male")
    case object Female extends Gender("female")

    val all = List(Male, Female)

    def from(value: String): Either[String, Gender] =
      all.find(_.value == value).toRight(s"unexpected item kind $value")

    implicit val decode: Decoder[Gender] = Decoder[String].emap(Gender.from)
    implicit val encode: Encoder[Gender] = Encoder[String].contramap(_.value)
  }

  sealed trait PaymentMethod

  final case class CreditCard(name: String, number: String, expiry: String, cvv: Int)
      extends PaymentMethod
  final case class Paypal(email: String) extends PaymentMethod

  final case class Payment(
      id: ObjectId,
      amount: BigDecimal,
      method: PaymentMethod,
      date: Instant
  )

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
      dob: LocalDate,
      address: Address,
      registrationDate: Instant
  )

  def person(
      firstName: String = "John",
      lastName: String = "Bloggs",
      gender: Gender = Gender.Male
  ): Person =
    Person(
      ObjectId(),
      gender,
      firstName,
      lastName,
      LocalDate.parse("1970-12-01"),
      Address(611, "5th Ave", "New York", "NY 10022"),
      Instant.now().`with`(MILLI_OF_SECOND, 0)
    )

}
