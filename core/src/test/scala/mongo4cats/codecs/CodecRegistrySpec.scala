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

package mongo4cats.codecs

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import mongo4cats.TestData
import mongo4cats.bson.Document
import mongo4cats.bson.syntax._
import mongo4cats.client.MongoClient
import mongo4cats.collection.operations.{Filter, Update}
import mongo4cats.database.MongoDatabase
import mongo4cats.embedded.EmbeddedMongo
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import scala.concurrent.Future

class CodecRegistrySpec extends AsyncWordSpec with Matchers with EmbeddedMongo {

  override val mongoPort: Int = 12349

  "A CodecRegistry" should {

    "be able to handle scala option" in {
      withEmbeddedMongoDatabase { db =>
        val result = for {
          coll <- db.getCollection("coll")
          _    <- coll.insertOne(TestData.gbpAccount)
          _    <- coll.updateMany(Filter.empty, Update.set("currency", None).set("name", Some("updated-acc")))
          doc  <- coll.find.first
        } yield doc.get

        result.map { doc =>
          doc.getString("currency") mustBe None
          doc.getString("name") mustBe Some("updated-acc")
        }
      }
    }

    "be able to handle scala map" in {
      withEmbeddedMongoDatabase { db =>
        val result = for {
          coll <- db.getCollection("coll")
          _    <- coll.insertOne(TestData.gbpAccount)
          _    <- coll.updateMany(Filter.empty, Update.set("props", Map("a" -> 42, "b" -> "foo")))
          doc  <- coll.find.first
        } yield doc.get

        result.map { doc =>
          doc.getDocument("props") mustBe Some(Document("a" := 42, "b" := "foo"))
        }
      }
    }

    "be able to handle scala iterables" in {
      withEmbeddedMongoDatabase { db =>
        val result = for {
          coll <- db.getCollection("coll")
          _    <- coll.insertOne(TestData.gbpAccount)
          _    <- coll.updateMany(Filter.empty, Update.set("tags", List("foo", "bar", 42)))
          doc  <- coll.find.first
        } yield doc.get

        result.map { doc =>
          doc.getList("tags") mustBe Some(List("foo".toBson, "bar".toBson, 42.toBson))
        }
      }
    }

    "be able to handle my document" in {
      withEmbeddedMongoDatabase { db =>
        val result = for {
          coll <- db.getCollectionWithCodec[Document]("coll")
          _    <- coll.insertOne(Document("foo" := "bar", "tags" := List("my", "doc")))
          doc  <- coll.find.first
        } yield doc.get

        result.map { doc =>
          doc.getString("foo") mustBe Some("bar")
          doc.getAs[List[String]]("tags") mustBe Some(List("my", "doc"))
          doc.getObjectId("_id") mustBe defined
        }
      }
    }
  }

  def withEmbeddedMongoDatabase[A](test: MongoDatabase[IO] => IO[A]): Future[A] =
    withRunningEmbeddedMongo {
      MongoClient
        .fromConnectionString[IO](s"mongodb://localhost:$mongoPort")
        .use { client =>
          client.getDatabase("db").flatMap(test)
        }
    }.unsafeToFuture()(IORuntime.global)
}
