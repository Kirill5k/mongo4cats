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

import mongo4cats.TestData
import mongo4cats.bson.Document
import mongo4cats.bson.syntax._
import mongo4cats.models.client.MongoConnection
import mongo4cats.models.collection._
import mongo4cats.operations.{Filter, Index, Sort, Update}
import mongo4cats.zio.embedded.EmbeddedMongo
import zio.stream.{ZSink, ZStream}
import zio.{Scope, ZIO, ZLayer}
import zio.test._
import zio.test.Assertion._
import zio.test.TestAspect.sequential

import java.util.UUID

object ZMongoCollectionSpec extends ZIOSpecDefault with EmbeddedMongo {

  override val mongoPort: Int = 27000

  override def spec: Spec[TestEnvironment with Scope, Any] = suite("A ZMongoCollection when")(
    suite("insertOne should")(
      test("store new document in db") {
        withEmbeddedMongoDatabase { db =>
          for {
            coll         <- db.getCollection("coll")
            insertResult <- coll.insertOne(TestData.gbpAccount)
            documents    <- coll.find.first
          } yield assert(documents)(isSome(equalTo(TestData.gbpAccount))) &&
            assert(insertResult.wasAcknowledged())(isTrue)
        }
      }
    ),
    suite("insertMany should")(
      test("store many documents in db") {
        withEmbeddedMongoDatabase { db =>
          for {
            coll         <- db.getCollection("coll")
            insertResult <- coll.insertMany(TestData.accounts)
            documents    <- coll.find.all
          } yield assert(documents)(equalTo(TestData.accounts)) &&
            assert(insertResult.wasAcknowledged())(isTrue) &&
            assert(insertResult.getInsertedIds.size())(equalTo(3))
        }
      }
    ),
    suite("count should")(
      test("return count of all documents in collection") {
        withEmbeddedMongoDatabase { db =>
          for {
            coll  <- db.getCollection("coll")
            _     <- coll.insertMany(TestData.accounts)
            count <- coll.count
          } yield assert(count)(equalTo(3L))
        }
      },
      test("return 0 for empty collection") {
        withEmbeddedMongoDatabase { db =>
          for {
            coll  <- db.getCollection("coll")
            count <- coll.count
          } yield assert(count)(equalTo(0L))
        }
      },
      test("apply filters") {
        withEmbeddedMongoDatabase { db =>
          for {
            coll  <- db.getCollection("coll")
            _     <- coll.insertMany(TestData.accounts)
            count <- coll.count(Filter.eq("currency", TestData.EUR))
          } yield assert(count)(equalTo(1L))
        }
      }
    ),
    suite("deleteMany should")(
      test("delete multiple docs in coll") {
        withEmbeddedMongoDatabase { db =>
          for {
            coll <- db.getCollection("coll")
            _    <- coll.insertMany(TestData.accounts)
            deleteResult <- coll.deleteMany(
              Filter.eq("currency", TestData.EUR) || Filter.eq("currency", TestData.GBP)
            )
            remaining <- coll.find.all
          } yield assert(remaining)(equalTo(List(TestData.usdAccount))) && assert(deleteResult.getDeletedCount)(equalTo(2L))
        }
      },
      test("not delete anything if filter doesn't match") {
        withEmbeddedMongoDatabase { db =>
          for {
            coll         <- db.getCollection("coll")
            _            <- coll.insertMany(TestData.accounts)
            deleteResult <- coll.deleteMany(Filter.eq("currency", "FOO"))
            count        <- coll.count
          } yield assert(count)(equalTo(3L)) && assert(deleteResult.getDeletedCount)(equalTo(0L))
        }
      }
    ),
    suite("deleteOne should")(
      test("delete one doc in coll") {
        withEmbeddedMongoDatabase { db =>
          for {
            coll         <- db.getCollection("coll")
            _            <- coll.insertMany(TestData.accounts)
            deleteResult <- coll.deleteOne(Filter.eq("currency", TestData.EUR).not)
            count        <- coll.count
          } yield assert(deleteResult.getDeletedCount)(equalTo(1L)) && assert(count)(equalTo(2L))
        }
      }
    ),
    suite("updateOne and updateMany should")(
      test("update one doc in coll") {
        withEmbeddedMongoDatabase { db =>
          for {
            coll         <- db.getCollection("coll")
            _            <- coll.insertMany(TestData.accounts)
            updateResult <- coll.updateOne(Filter.eq("currency", TestData.EUR), Update.set("name", "eur-account"))
            docs         <- coll.find.filter(Filter.eq("currency", TestData.EUR)).first
          } yield assert(docs.flatMap(_.getString("name")))(isSome(equalTo("eur-account"))) &&
            assert(updateResult)(hasField("matchedCount", _.getMatchedCount, equalTo(1L))) &&
            assert(updateResult)(hasField("modifiedCount", _.getModifiedCount, equalTo(1L)))
        }
      },
      test("update many docs in coll") {
        withEmbeddedMongoDatabase { db =>
          for {
            coll         <- db.getCollection("coll")
            _            <- coll.insertMany(TestData.accounts)
            updateResult <- coll.updateMany(Filter.eq("currency", TestData.EUR).not, Update.set("status", "updated"))
            docs         <- coll.find(Filter.eq("currency", TestData.EUR).not).all
          } yield assert(docs)(hasSize(equalTo(2))) &&
            assert(docs.flatMap(_.getString("status")).toSet)(equalTo(Set("updated"))) &&
            assert(updateResult)(hasField("matchedCount", _.getMatchedCount, equalTo(2L))) &&
            assert(updateResult)(hasField("modifiedCount", _.getModifiedCount, equalTo(2L)))
        }
      },
      test("handle uuid") {
        withEmbeddedMongoDatabase { db =>
          val uuid = UUID.fromString("29ca24a5-8e95-4fc1-bec0-7c0d08de5196")
          for {
            coll <- db.getCollection("coll")
            _    <- coll.insertOne(Document("foo" := "bar"))
            _    <- coll.updateMany(Filter.eq("foo", "bar"), Update.set("uuid", uuid))
            doc  <- coll.find(Filter.eq("foo", "bar")).first
          } yield assert(doc.flatMap(_.getAs[UUID]("uuid")))(isSome[UUID](equalTo(uuid)))
        }
      },
      test("update all docs in coll") {
        withEmbeddedMongoDatabase { db =>
          for {
            coll <- db.getCollection("coll")
            _    <- coll.insertMany(TestData.accounts)
            updateQuery = Update.set("status", "updated").rename("currency", "curr").currentDate("updatedAt")
            updateResult <- coll.updateMany(Filter.empty, updateQuery)
            docs         <- coll.find.all
          } yield assert(docs)(hasSize(equalTo(3))) &&
            assert(docs.flatMap(_.getString("status")).toSet)(equalTo(Set("updated"))) &&
            assert(updateResult)(hasField("matchedCount", _.getMatchedCount, equalTo(3L))) &&
            assert(updateResult)(hasField("modifiedCount", _.getModifiedCount, equalTo(3L)))
        }
      },
      test("combine multiple updates together") {
        withEmbeddedMongoDatabase { db =>
          for {
            coll <- db.getCollection("coll")
            _    <- coll.insertMany(TestData.accounts)
            updateQuery = Update
              .set("status", "updated")
              .combinedWith(Update.rename("currency", "money"))
              .combinedWith(Update.unset("name"))
            updateResult <- coll.updateMany(Filter.empty, updateQuery)
            docs         <- coll.find.all
          } yield assert(docs)(hasSize(equalTo(3))) &&
            assert(docs.flatMap(_.getString("status")).toSet)(equalTo(Set("updated"))) &&
            assert(updateResult)(hasField("matchedCount", _.getMatchedCount, equalTo(3L))) &&
            assert(updateResult)(hasField("modifiedCount", _.getModifiedCount, equalTo(3L))) &&
            assert(docs)(forall(hasField("status", _.getString("status"), isSome(equalTo("updated"))))) &&
            assert(docs)(forall(hasField("money", _.contains("money"), isTrue))) &&
            assert(docs)(forall(hasField("currency", _.contains("currency"), isFalse)))
        }
      }
    ),
    suite("replaceOne should")(
      test("replace doc in coll") {
        withEmbeddedMongoDatabase { db =>
          for {
            coll <- db.getCollection("coll")
            _    <- coll.insertOne(TestData.gbpAccount)
            replacement = Document("currency" := TestData.EUR)
            updateResult <- coll.replaceOne(Filter.eq("currency", TestData.GBP), replacement)
            docs         <- coll.find.all
          } yield assert(updateResult)(hasField("matchedCount", _.getMatchedCount, equalTo(1L))) &&
            assert(updateResult)(hasField("modifiedCount", _.getModifiedCount, equalTo(1L))) &&
            assert(docs)(hasSize(equalTo(1))) &&
            assert(docs.head.getDocument("currency"))(isSome(equalTo(TestData.EUR)))
        }
      }
    ),
    suite("findOneAndReplace should")(
      test("find and replace doc in coll") {
        withEmbeddedMongoDatabase { db =>
          for {
            coll <- db.getCollection("coll")
            _    <- coll.insertOne(TestData.eurAccount)
            old  <- coll.findOneAndReplace(Filter.eq("currency", TestData.EUR), Document("currency" := TestData.GBP))
            docs <- coll.find.all
          } yield assert(old)(isSome(equalTo(TestData.eurAccount))) &&
            assert(docs.head)(hasField("currency", _.getDocument("currency"), isSome(equalTo(TestData.GBP))))
        }
      }
    ),
    suite("findOneAndUpdate should")(
      test("find and update doc in coll") {
        withEmbeddedMongoDatabase { db =>
          for {
            coll <- db.getCollection("coll")
            _    <- coll.insertMany(TestData.accounts)
            old  <- coll.findOneAndUpdate(Filter.eq("currency", TestData.EUR), Update.set("status", "updated"))
            docs <- coll.find.filter(Filter.exists("status")).all
          } yield assert(old)(isSome(equalTo(TestData.eurAccount))) &&
            assert(docs)(equalTo(List(TestData.eurAccount += ("status" -> "updated"))))
        }
      }
    ),
    suite("findOneAndDelete should")(
      test("find and delete doc in coll") {
        withEmbeddedMongoDatabase { db =>
          for {
            coll <- db.getCollection("coll")
            _    <- coll.insertMany(TestData.accounts)
            old  <- coll.findOneAndDelete(Filter.eq("name", "eur-acc"))
            docs <- coll.find.all
          } yield assert(docs)(hasSize(equalTo(2))) &&
            assert(docs.flatMap(_.getString("name")))(hasSameElements(List("gbp-acc", "usd-acc"))) &&
            assert(old.flatMap(_.getString("name")))(isSome(equalTo("eur-acc")))
        }
      }
    ),
    suite("find should")(
      test("find docs by field") {
        withEmbeddedMongoDatabase { db =>
          for {
            coll <- db.getCollection("coll")
            _    <- coll.insertMany(TestData.accounts)
            res  <- coll.find.filter(Filter.eq("currency", TestData.EUR)).all
          } yield assert(res)(equalTo(List(TestData.eurAccount)))
        }
      },
      test("get all docs with sort, skip and limit") {
        withEmbeddedMongoDatabase { db =>
          for {
            coll <- db.getCollection("coll")
            _    <- coll.insertMany(TestData.categories)
            res  <- coll.find.sortByDesc("name").skip(2).limit(3).all
          } yield assert(res.flatMap(_.getString("name")))(equalTo(List("cat-7", "cat-6", "cat-5")))
        }
      },
      test("get first doc with sort, skip and limit") {
        withEmbeddedMongoDatabase { db =>
          for {
            coll <- db.getCollection("coll")
            _    <- coll.insertMany(TestData.categories)
            res  <- coll.find.sort(Sort.desc("name")).skip(3).limit(2).first
          } yield assert(res.flatMap(_.getString("name")))(isSome(equalTo("cat-6")))
        }
      },
      test("return none when there are no docs that match query") {
        withEmbeddedMongoDatabase { db =>
          for {
            coll <- db.getCollection("coll")
            res  <- coll.find.sort(Sort.desc("name")).skip(3).limit(2).first
          } yield assert(res)(isNone)
        }
      },
      test("stream with filter") {
        withEmbeddedMongoDatabase { db =>
          for {
            coll <- db.getCollection("coll")
            _    <- coll.insertMany(TestData.categories(50000))
            res  <- coll.find.filter(Filter.regex("name", "cat-(1|3|5).*")).stream.run(ZSink.collectAll)
          } yield assert(res)(hasSize(equalTo(23333)))
        }
      },
      test("bounded stream") {
        withEmbeddedMongoDatabase { db =>
          for {
            cats <- db.getCollection("categories")
            txs  <- db.getCollection("transactions")
            _    <- cats.insertMany(TestData.categories)
            _    <- txs.insertMany(TestData.transactions(1000000))
            res  <- txs.find.boundedStream(100).run(ZSink.count)
          } yield assert(res)(equalTo(1000000L))
        }
      },
      test("execute multiple bounded streams in parallel") {
        withEmbeddedMongoDatabase { db =>
          for {
            cats <- db.getCollection("categories")
            txs  <- db.getCollection("transactions")
            _    <- cats.insertMany(TestData.categories)
            _    <- txs.insertMany(TestData.transactions(1000000))
            res <- ZStream
              .mergeAllUnbounded(512)(
                txs.find.skip(10000).limit(10000).boundedStream(124),
                txs.find.skip(20000).limit(10000).boundedStream(124),
                txs.find.skip(30000).limit(10000).boundedStream(124),
                txs.find.skip(40000).limit(10000).boundedStream(124),
                txs.find.skip(50000).limit(10000).boundedStream(124)
              )
              .run(ZSink.collectAll)
          } yield assert(res)(hasSize(equalTo(50000)))
        }
      }
    ),
    suite("distinct should")(
      test("return distinct fields of a doc") {
        withEmbeddedMongoDatabase { db =>
          for {
            coll <- db.getCollection("coll")
            _    <- coll.insertMany(TestData.accounts)
            res  <- coll.distinct[Document]("currency").all
          } yield assert(res)(hasSameElements(Set(TestData.USD, TestData.EUR, TestData.GBP)))
        }
      }
    ),
    suite("bulkWrite should")(
      test("perform multiple operations at once") {
        withEmbeddedMongoDatabase { db =>
          for {
            coll <- db.getCollection("coll")
            _    <- coll.insertMany(TestData.accounts)
            res <- coll.bulkWrite(
              List(
                WriteCommand.InsertOne(TestData.lvlAccount),
                WriteCommand.DeleteOne(Filter.eq("name", "eur-acc")),
                WriteCommand.UpdateOne(Filter.eq("name", "gbp-acc"), Update.set("foo", "bar"))
              )
            )
          } yield assert(res.getDeletedCount)(equalTo(1)) &&
            assert(res.getModifiedCount)(equalTo(1)) &&
            assert(res.getInsertedCount)(equalTo(1))
        }
      }
    ),
    suite("renameCollection should")(
      test("change collection name and keep the data") {
        withEmbeddedMongoDatabase { db =>
          for {
            coll  <- db.getCollection("coll")
            _     <- coll.insertMany(TestData.accounts)
            _     <- coll.renameCollection(MongoNamespace("test-db", "coll2"), RenameCollectionOptions(dropTarget = false))
            coll2 <- db.getCollection("coll2")
            count <- coll2.count
          } yield assert(coll2.namespace.collectionName)(equalTo("coll2")) &&
            assert(count)(equalTo(3L))
        }
      }
    ),
    suite("createIndex should")(
      List(
        (
          Index.ascending("name"),
          IndexOptions().unique(true),
          List(
            "{v: 2, key: {_id: 1}, name: '_id_'}",
            "{v: 2, key: {name: 1}, unique: true, name: 'name_1'}"
          )
        ),
        (
          Index.descending("name"),
          IndexOptions(),
          List(
            "{v: 2, key: {_id: 1}, name: '_id_'}",
            "{v: 2, key: {name: -1}, name: 'name_-1'}"
          )
        ),
        (
          Index.ascending("name").combinedWith(Index.descending("currency")),
          IndexOptions(),
          List(
            "{v: 2, key: {_id: 1}, name: '_id_'}",
            "{v: 2, key: {name: 1, currency: -1}, name: 'name_1_currency_-1'}"
          )
        ),
        (
          Index.geo2dsphere("location"),
          IndexOptions(),
          List(
            "{v: 2, key: {_id: 1}, name: '_id_'}",
            "{v: 2, key: {location: '2dsphere'}, name: 'location_2dsphere', '2dsphereIndexVersion': 3}"
          )
        ),
        (
          Index.geo2d("location"),
          IndexOptions(),
          List(
            "{v: 2, key: {_id: 1}, name: '_id_'}",
            "{v: 2, key: {location: '2d'}, name: 'location_2d'}"
          )
        ),
        (
          Index.text("name"),
          IndexOptions(),
          List(
            "{v: 2, key: {_id: 1}, name: '_id_'}",
            "{v: 2, key: {_fts: 'text', _ftsx: 1}, name: 'name_text', weights: {name: 1}, default_language: 'english', language_override: 'language', textIndexVersion: 3}"
          )
        ),
        (
          Index.hashed("name"),
          IndexOptions(),
          List(
            "{v: 2, key: {_id: 1}, name: '_id_'}",
            "{v: 2, key: {name: 'hashed'}, name: 'name_hashed'}"
          )
        )
      ).map { case (index, options, expected) =>
        test(s"create index $index") {
          withEmbeddedMongoDatabase { db =>
            for {
              coll    <- db.getCollection("coll")
              _       <- coll.createIndex(index, options)
              indexes <- coll.listIndexes
            } yield assert(indexes)(hasSameElements(expected.map(Document.parse)))
          }
        }
      }
    )
  ) @@ sequential

  def withEmbeddedMongoDatabase[A](test: ZMongoDatabase => ZIO[Any, Throwable, A]): ZIO[Scope, Throwable, A] =
    withRunningEmbeddedMongo {
      ZIO
        .serviceWithZIO[ZMongoDatabase](test(_))
        .provide(
          ZLayer.scoped(ZMongoClient.fromConnection(MongoConnection.classic("localhost", mongoPort))),
          ZLayer.fromZIO(ZIO.serviceWithZIO[ZMongoClient](_.getDatabase("test-db")))
        )
    }
}
