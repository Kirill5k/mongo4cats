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
import cats.effect.unsafe.implicits.global
import mongo4cats.embedded.EmbeddedMongo
import mongo4cats.client.MongoClientF
import mongo4cats.bson.Document
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import scala.concurrent.Future

class MongoDatabaseFSpec extends AsyncWordSpec with Matchers with EmbeddedMongo {

  override val mongoPort: Int = 12346

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
          _     <- db.createCollection("c1", CreateCollectionOptions().capped(true).sizeInBytes(1024L))
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
  }

  def withEmbeddedMongoClient[A](test: MongoClientF[IO] => IO[A]): Future[A] =
    withRunningEmbeddedMongo {
      MongoClientF
        .fromConnectionString[IO](s"mongodb://localhost:$mongoPort")
        .use(test)
    }.unsafeToFuture()
}
