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

package mongo4cats.database

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import mongo4cats.EmbeddedMongo
import mongo4cats.client.MongoClientF
import org.bson.Document
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.mongodb.scala.bson.codecs.Macros._
import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}
import org.mongodb.scala.MongoClient.DEFAULT_CODEC_REGISTRY
import org.mongodb.scala.bson.ObjectId

class MongoDatabaseFSpec extends AnyWordSpec with Matchers with EmbeddedMongo {

  implicit val runTime = IORuntime.global

  "A MongoDatabaseF" should {

    "return db name" in {
      withEmbeddedMongoClient { client =>
        client.getDatabase("foo").map { db =>
          db.name mustBe "foo"
        }
      }
    }

    "create new collections and return collection names" in {
      withEmbeddedMongoClient { client =>
        val result = for {
          db    <- client.getDatabase("foo")
          _     <- db.createCollection("c1")
          _     <- db.createCollection("c2")
          names <- db.collectionNames
        } yield names

        result.map(_ mustBe List("c2", "c1"))
      }
    }

    "return document collection by name" in {
      withEmbeddedMongoClient { client =>
        val result = for {
          db         <- client.getDatabase("foo")
          _          <- db.createCollection("c1")
          collection <- db.getCollection("c1")
        } yield collection

        result.map { col =>
          col.namespace.getDatabaseName mustBe "foo"
          col.namespace.getCollectionName mustBe "c1"
          col.documentClass mustBe classOf[Document]
        }
      }
    }

    "return specific class collection by name" in {
      final case class Person(_id: ObjectId, firstName: String, lastName: String)

      val personCodecRegistry = fromRegistries(fromProviders(classOf[Person]), DEFAULT_CODEC_REGISTRY)

      withEmbeddedMongoClient { client =>
        val result = for {
          db         <- client.getDatabase("foo")
          _          <- db.createCollection("c1")
          collection <- db.getCollection[Person]("c1", personCodecRegistry)
        } yield collection

        result.map { col =>
          col.namespace.getDatabaseName mustBe "foo"
          col.namespace.getCollectionName mustBe "c1"
          col.documentClass mustBe classOf[Person]
        }
      }
    }
  }

  def withEmbeddedMongoClient[A](test: MongoClientF[IO] => IO[A]): A =
    withRunningEmbeddedMongo(port = 12346) {
      MongoClientF
        .fromConnectionString[IO]("mongodb://localhost:12346")
        .use(test)
        .unsafeRunSync()
    }
}
