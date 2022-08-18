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
import com.mongodb.client.model.Filters
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
import mongo4cats.derivation.bson._
import mongo4cats.derivation.bson.tag._
import mongo4cats.derivation.bson.derivation.decoder.auto._
import mongo4cats.derivation.bson.derivation.encoder.auto._
import mongo4cats.derivation.bson.{BsonDecoder, BsonEncoder, BsonValueOps}
import org.bson.{BsonReader, BsonValue, BsonWriter}
import org.bson.codecs.{Codec, DecoderContext, EncoderContext}
import org.bson.codecs.configuration.CodecProvider

import scala.concurrent.Future
import scala.reflect.ClassTag

class MongoBsonCollectionSpec extends AsyncWordSpec with Matchers with EmbeddedMongo {

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

    "use bson-value-codec-provider like circe-codec-provider for encoding and decoding data" in {
      withEmbeddedMongoClient { client =>
        val p  = person()
        val ps = (0 to 1000).map(i => person(s"p-${i}", s"p-${i}")).toList

        val result = for {
          // --- Circe ---
          db     <- client.getDatabase("test")
          _      <- db.createCollection("people")
          coll   <- db.getCollectionWithCodec[Person]("people")
          _      <- coll.insertOne(p)
          people <- coll.find.all

          // --- BsonValue ---
          collBson <- db.getCollectionWithCodec[Fast[Person]]("people")

          // Parallel Inserts to check "thread safety" of `MongoCodecProvider[_]`.
          _           <- fs2.Stream.emits(ps).covary[IO].parEvalMapUnordered(maxConcurrent = 20)(collBson.insertOne(_)).compile.drain
          peoples     <- coll.find.all.map(_.toSet)
          peoplesBson <- collBson.find.all.map(_.toSet)
          _ = assert(peoplesBson == peoples)

          // Parallel Reads to check "thread safety" of `MongoCodecProvider[_]`.
          peoplesBson2 <- fs2.Stream
            .iterable(peoplesBson.map(_._id))
            .covary[IO]
            .parEvalMapUnordered(maxConcurrent = 20)(id =>
              collBson
                .find(Filters.eq(id)) // Java API.
                // .find(Filter.idEq(id)) // Scala API.
                .first
            )
            .collect { case Some(p) => p }
            .compile
            .toList
          _ = assert(peoplesBson2.toSet == peoples)

        } yield peoples

        result.map(_ mustBe (ps.toSet + p))
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
