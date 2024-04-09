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
import com.mongodb.{ReadConcern, ReadPreference, WriteConcern}
import mongo4cats.bson.Document
import mongo4cats.bson.syntax._
import mongo4cats.client.MongoClient
import mongo4cats.models.database.CreateCollectionOptions
import mongo4cats.embedded.EmbeddedMongo
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import scala.concurrent.Future

class MongoDatabaseSpec extends AsyncWordSpec with Matchers with EmbeddedMongo {

  override val mongoPort: Int = 12348

  "A MongoDatabase" when {

    "updating preferences" should {
      "return db name" in withEmbeddedMongoClient { client =>
        client.getDatabase("foo").map { db =>
          db.name mustBe "foo"
        }
      }

      "set write concern" in withEmbeddedMongoClient { client =>
        val result = for {
          db <- client.getDatabase("test")
          updDb = db.withWriteConcern(WriteConcern.UNACKNOWLEDGED)
          wc    = updDb.writeConcern
        } yield wc

        result.map(_ mustBe WriteConcern.UNACKNOWLEDGED)
      }

      "set read concern" in withEmbeddedMongoClient { client =>
        val result = for {
          db <- client.getDatabase("test")
          updDb = db.withReadConcern(ReadConcern.MAJORITY)
          rc    = updDb.readConcern
        } yield rc

        result.map(_ mustBe ReadConcern.MAJORITY)
      }

      "set read preference" in withEmbeddedMongoClient { client =>
        val result = for {
          db <- client.getDatabase("test")
          updDb = db.withReadPreference(ReadPreference.primaryPreferred())
          rc    = updDb.readPreference
        } yield rc

        result.map(_ mustBe ReadPreference.primaryPreferred())
      }
    }

    "interacting with collections" should {
      "create new collections and return collection names" in withEmbeddedMongoClient { client =>
        val result = for {
          db    <- client.getDatabase("foo")
          _     <- db.createCollection("c1", CreateCollectionOptions().capped(true).sizeInBytes(1024L))
          _     <- db.createCollection("c2")
          names <- db.listCollectionNames
        } yield names

        result.map(_.toSet mustBe Set("c2", "c1"))
      }

      "return current collections" in withEmbeddedMongoClient { client =>
        val result = for {
          db    <- client.getDatabase("foo")
          _     <- db.createCollection("c1")
          colls <- db.listCollections
        } yield colls

        result.map { colls =>
          colls must have size 1
          val c1 = colls.head
          c1.getString("name") mustBe Some("c1")
          c1.getString("type") mustBe Some("collection")
        }
      }

      "return document collection by name" in withEmbeddedMongoClient { client =>
        val result = for {
          db         <- client.getDatabase("foo")
          _          <- db.createCollection("c1")
          collection <- db.getCollection("c1")
        } yield collection

        result.map { col =>
          col.namespace.databaseName mustBe "foo"
          col.namespace.collectionName mustBe "c1"
          col.documentClass mustBe classOf[Document]
        }
      }
    }

    "drop" should {
      "delete a database" in withEmbeddedMongoClient { client =>
        val result = for {
          db  <- client.getDatabase("foo")
          _   <- db.createCollection("c1")
          _   <- db.drop
          dbs <- client.listDatabaseNames
        } yield dbs

        result.map { dbs =>
          dbs must not contain "foo"
        }
      }
    }

    "runCommand" should {
      "run a specific command" in withEmbeddedMongoClient { client =>
        val result = for {
          db <- client.getDatabase("admin")
          _ <- db.runCommand(Document("setParameter" := 1, "cursorTimeoutMillis" := 300000L))
        } yield ()

        result.map(_ mustBe ())
      }
    }
  }

  def withEmbeddedMongoClient[A](test: MongoClient[IO] => IO[A]): Future[A] =
    withRunningEmbeddedMongo {
      MongoClient
        .fromConnectionString[IO](s"mongodb://localhost:$mongoPort")
        .use(test)
    }.unsafeToFuture()(IORuntime.global)
}
