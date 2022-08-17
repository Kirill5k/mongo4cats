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

import cats.syntax.all._
import cats.effect.IO
import cats.effect.unsafe.IORuntime
import io.circe.generic.auto._
import mongo4cats.bson.ObjectId
import mongo4cats.circe._
import mongo4cats.client.MongoClient
import mongo4cats.codecs.{BsonValueCodecProvider, CodecRegistry, MongoCodecProvider}
import mongo4cats.collection.operations.Filter
import mongo4cats.embedded.EmbeddedMongo
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import mongo4cats.derivation.bson.AllBsonEncoders._
import mongo4cats.derivation.bson.AllBsonDecoders._
import mongo4cats.derivation.bson.derivation.decoder.auto._
import mongo4cats.derivation.bson.derivation.encoder.auto._
import mongo4cats.derivation.bson.{BsonDecoder, BsonEncoder, BsonValueOps}
import org.bson.BsonValue

import scala.concurrent.Future

class MongoBsonCollectionSpec extends AsyncWordSpec with Matchers with EmbeddedMongo {

  val javaBsonValueRegistry =
    org.bson.codecs.configuration.CodecRegistries.fromProviders(new org.bson.codecs.BsonValueCodecProvider())

  override val mongoPort: Int = 12348

  "A MongoCollection" should {

    final case class Address(
        streetNumber: Int,
        streetName: String,
        city: String,
        postcode: String
    )

    final case class Person(
        _id: ObjectId,
        firstName: String,
        lastName: String,
        address: Address
    )

    "use circe-codec-provider for encoding and decoding data" in {
      withEmbeddedMongoClient { client =>
        val p = person()
        val result = for {
          db     <- client.getDatabase("test")
          _      <- db.createCollection("people")
          coll   <- db.getCollectionWithCodec[Person]("people")
          _      <- coll.insertOne(p)
          people <- coll.find.all

          // --- Bson ---
          collBson   <- db.getCollection[BsonValue]("people", javaBsonValueRegistry)
          peopleBson <- collBson.find.all
          _ = assert(peopleBson.toList.traverse(BsonDecoder[Person].apply(_)) == people.toList.asRight)
        } yield people

        result.map(_ mustBe List(p))
      }
    }

    def person(firstName: String = "John", lastName: String = "Bloggs"): Person =
      Person(
        ObjectId(),
        firstName,
        lastName,
        Address(611, "5th Ave", "New York", "NY 10022")
      )
  }

  def withEmbeddedMongoClient[A](test: MongoClient[IO] => IO[A]): Future[A] =
    withRunningEmbeddedMongo {
      MongoClient
        .fromConnectionString[IO](s"mongodb://localhost:$mongoPort")
        .use(test)
    }.unsafeToFuture()(IORuntime.global)
}
