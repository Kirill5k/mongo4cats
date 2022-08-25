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

package mongo4cats.zio

import com.mongodb.{ReadConcern, ReadPreference, WriteConcern}
import mongo4cats.bson.Document
import mongo4cats.models.collection.MongoNamespace
import mongo4cats.models.database.CreateCollectionOptions
import mongo4cats.zio.embedded.EmbeddedMongo
import zio.{Scope, ZIO, ZLayer}
import zio.test._
import zio.test.Assertion._
import zio.test.TestAspect.sequential

object ZMongoDatabaseSpec extends ZIOSpecDefault with EmbeddedMongo {

  override val mongoPort: Int = 27001

  override def spec: Spec[TestEnvironment with Scope, Any] = suite("A ZMongoDatabase when")(
    suite("updating preferences should")(
      test("return db name") {
        withEmbeddedMongoClient { client =>
          client.getDatabase("foo").map { db =>
            assert(db.name)(equalTo("foo"))
          }
        }
      },
      test("set write concern") {
        withEmbeddedMongoClient { client =>
          for {
            db <- client.getDatabase("test")
            updDb = db.withWriteConcern(WriteConcern.UNACKNOWLEDGED)
            wc    = updDb.writeConcern
          } yield assert(wc)(equalTo(WriteConcern.UNACKNOWLEDGED))
        }
      },
      test("set read concern") {
        withEmbeddedMongoClient { client =>
          for {
            db <- client.getDatabase("test")
            updDb = db.witReadConcern(ReadConcern.MAJORITY)
            rc    = updDb.readConcern
          } yield assert(rc)(equalTo(ReadConcern.MAJORITY))
        }
      },
      test("set read preference") {
        withEmbeddedMongoClient { client =>
          for {
            db <- client.getDatabase("test")
            updDb = db.withReadPreference(ReadPreference.primaryPreferred())
            rc    = updDb.readPreference
          } yield assert(rc)(equalTo(ReadPreference.primaryPreferred()))
        }
      }
    ),
    suite("interacting with collections should")(
      test("create new collections and return collection names") {
        withEmbeddedMongoClient { client =>
          for {
            db    <- client.getDatabase("foo")
            _     <- db.createCollection("c1", CreateCollectionOptions().capped(true).sizeInBytes(1024L))
            _     <- db.createCollection("c2")
            names <- db.listCollectionNames
          } yield assert(names)(hasSameElements(Set("c2", "c1")))
        }
      },
      test("return current collections") {
        withEmbeddedMongoClient { client =>
          for {
            db    <- client.getDatabase("foo")
            _     <- db.createCollection("c1")
            colls <- db.listCollections
          } yield assert(colls)(hasSize(equalTo(1))) &&
            assert(colls.head.getString("name"))(isSome(equalTo("c1"))) &&
            assert(colls.head.getString("type"))(isSome(equalTo("collection")))
        }
      },
      test("return document collection by name") {
        withEmbeddedMongoClient { client =>
          for {
            db   <- client.getDatabase("foo")
            _    <- db.createCollection("c1")
            coll <- db.getCollection("c1")
          } yield assert(coll.namespace)(equalTo(MongoNamespace("foo", "c1"))) &&
            assert(coll.documentClass)(equalTo(classOf[Document]))
        }
      }
    ),
    suite("drop should")(
      test("delete a database") {
        withEmbeddedMongoClient { client =>
          for {
            db  <- client.getDatabase("foo")
            _   <- db.createCollection("c1")
            _   <- db.drop
            dbs <- client.listDatabaseNames
          } yield assert(dbs)(not(contains("c1")))
        }
      }
    )
  ) @@ sequential

  def withEmbeddedMongoClient[A](test: ZMongoClient => ZIO[Any, Throwable, A]): ZIO[Scope, Throwable, A] =
    withRunningEmbeddedMongo {
      ZIO
        .serviceWithZIO[ZMongoClient](test(_))
        .provide(
          ZLayer.scoped(ZMongoClient.fromConnectionString(s"mongodb://localhost:$mongoPort"))
        )
    }
}
