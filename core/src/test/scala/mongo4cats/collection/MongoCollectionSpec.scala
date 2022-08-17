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

package mongo4cats.collection

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import cats.syntax.parallel._
import com.mongodb.{MongoNamespace, ReadConcern, ReadPreference, WriteConcern}
import mongo4cats.TestData
import mongo4cats.embedded.EmbeddedMongo
import mongo4cats.bson.Document
import mongo4cats.bson.syntax._
import mongo4cats.client.MongoClient
import mongo4cats.collection.operations.{Filter, Index, Sort, Update}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import fs2.Stream
import mongo4cats.database.MongoDatabase
import org.scalatest.prop.TableDrivenPropertyChecks

import scala.concurrent.Future

class MongoCollectionSpec extends AsyncWordSpec with TableDrivenPropertyChecks with Matchers with EmbeddedMongo {

  override val mongoPort = 12347

  "A MongoCollection" when {
    "updating preferences" should {
      "set write concern" in {
        withEmbeddedMongoDatabase { db =>
          val result = for {
            coll <- db.getCollection("coll")
            updColl = coll.withWriteConcern(WriteConcern.UNACKNOWLEDGED)
            wc      = updColl.writeConcern
          } yield wc

          result.map(_ mustBe WriteConcern.UNACKNOWLEDGED)
        }
      }

      "set read concern" in {
        withEmbeddedMongoDatabase { db =>
          val result = for {
            coll <- db.getCollection("coll")
            updColl = coll.withReadConcern(ReadConcern.MAJORITY)
            rc      = updColl.readConcern
          } yield rc

          result.map(_ mustBe ReadConcern.MAJORITY)
        }
      }

      "set read preference" in {
        withEmbeddedMongoDatabase { db =>
          val result = for {
            coll <- db.getCollection("coll")
            updColl = coll.withReadPreference(ReadPreference.primaryPreferred())
            rc      = updColl.readPreference
          } yield rc

          result.map(_ mustBe ReadPreference.primaryPreferred())
        }
      }
    }

    "working with Documents" should {

      "insertOne" should {
        "store new document in db" in {
          withEmbeddedMongoDatabase { db =>
            val result = for {
              coll         <- db.getCollection("coll")
              insertResult <- coll.insertOne(TestData.gbpAccount)
              documents    <- coll.find.first
            } yield (insertResult, documents)

            result.map { case (insertRes, documents) =>
              documents mustBe Some(TestData.gbpAccount)
              insertRes.wasAcknowledged() mustBe true
            }
          }
        }
      }

      "insertMany" should {
        "store several documents in db" in {
          withEmbeddedMongoDatabase { db =>
            val result = for {
              coll         <- db.getCollection("coll")
              insertResult <- coll.insertMany(TestData.accounts)
              documents    <- coll.find.all
            } yield (insertResult, documents)

            result.map { case (insertRes, documents) =>
              documents mustBe TestData.accounts
              insertRes.wasAcknowledged() mustBe true
              insertRes.getInsertedIds must have size 3
            }
          }
        }
      }

      "count" should {
        "return count of all documents in collection" in {
          withEmbeddedMongoDatabase { db =>
            val result = for {
              coll  <- db.getCollection("coll")
              _     <- coll.insertMany(TestData.accounts)
              count <- coll.count
            } yield count

            result.map(_ mustBe 3)
          }
        }

        "return 0 for empty collection" in {
          withEmbeddedMongoDatabase { db =>
            val result = for {
              coll  <- db.getCollection("coll")
              count <- coll.count
            } yield count

            result.map(_ mustBe 0)
          }
        }

        "apply filters" in {
          withEmbeddedMongoDatabase { db =>
            val result = for {
              coll  <- db.getCollection("coll")
              _     <- coll.insertMany(TestData.accounts)
              count <- coll.count(Filter.eq("currency", TestData.EUR))
            } yield count

            result.map(_ mustBe 1)
          }
        }
      }

      "deleteMany" should {
        "delete multiple docs in coll" in {
          withEmbeddedMongoDatabase { db =>
            val result = for {
              coll <- db.getCollection("coll")
              _    <- coll.insertMany(TestData.accounts)
              deleteResult <- coll.deleteMany(
                Filter.eq("currency", TestData.EUR) || Filter.eq("currency", TestData.GBP)
              )
              count <- coll.count
            } yield (deleteResult, count)

            result.map { case (deleteRes, count) =>
              count mustBe 1
              deleteRes.getDeletedCount mustBe 2
            }
          }
        }
      }

      "deleteOne" should {
        "delete one doc in coll" in {
          withEmbeddedMongoDatabase { db =>
            val result = for {
              coll         <- db.getCollection("coll")
              _            <- coll.insertMany(TestData.accounts)
              deleteResult <- coll.deleteOne(Filter.eq("currency", TestData.EUR).not)
              count        <- coll.count
            } yield (deleteResult, count)

            result.map { case (deleteRes, count) =>
              count mustBe 2
              deleteRes.getDeletedCount mustBe 1
            }

          }
        }
      }

      "replaceOne" should {
        "replace doc in coll" in {
          withEmbeddedMongoDatabase { db =>
            val result = for {
              coll <- db.getCollection("coll")
              _    <- coll.insertOne(TestData.gbpAccount)
              replacement = Document("currency" := TestData.EUR)
              updateResult <- coll.replaceOne(Filter.eq("currency", TestData.GBP), replacement)
              docs         <- coll.find.all
            } yield (updateResult, docs)

            result.map { case (updateRes, docs) =>
              docs must have size 1
              docs.head.getDocument("currency") mustBe Some(TestData.EUR)
              updateRes.getMatchedCount mustBe 1
              updateRes.getModifiedCount mustBe 1
            }
          }
        }
      }

      "updateOne and updateMany" should {
        "update one doc in coll" in {
          withEmbeddedMongoDatabase { db =>
            val result = for {
              coll         <- db.getCollection("coll")
              _            <- coll.insertMany(TestData.accounts)
              updateResult <- coll.updateOne(Filter.eq("currency", TestData.EUR), Update.set("name", "eur-account"))
              docs         <- coll.find.filter(Filter.eq("currency", TestData.EUR)).first
            } yield (updateResult, docs)

            result.map { case (updateRes, docs) =>
              docs.flatMap(_.getString("name")) mustBe Some("eur-account")
              updateRes.getMatchedCount mustBe 1
              updateRes.getModifiedCount mustBe 1
            }

          }
        }

        "update many docs in coll" in {
          withEmbeddedMongoDatabase { db =>
            val result = for {
              coll         <- db.getCollection("coll")
              _            <- coll.insertMany(TestData.accounts)
              updateResult <- coll.updateMany(Filter.eq("currency", TestData.EUR).not, Update.set("status", "updated"))
              docs         <- coll.find(Filter.eq("currency", TestData.EUR).not).all
            } yield (updateResult, docs)

            result.map { case (updateRes, docs) =>
              docs must have size 2
              docs.flatMap(_.getString("status")).toSet mustBe Set("updated")
              updateRes.getMatchedCount mustBe 2
              updateRes.getModifiedCount mustBe 2
            }
          }
        }

        "update all docs in coll" in {
          withEmbeddedMongoDatabase { db =>
            val result = for {
              coll <- db.getCollection("coll")
              _    <- coll.insertMany(TestData.accounts)
              updateQuery = Update.set("status", "updated").rename("currency", "curr").currentDate("updatedAt")
              updateResult <- coll.updateMany(Filter.empty, updateQuery)
              docs         <- coll.find.all
            } yield (updateResult, docs)

            result.map { case (updateRes, docs) =>
              docs must have size 3
              docs.flatMap(_.getString("status")).toSet mustBe Set("updated")
              docs.forall(_.contains("curr")) mustBe true
              docs.forall(!_.contains("currency")) mustBe true
              docs.forall(_.contains("updatedAt")) mustBe true
              updateRes.getMatchedCount mustBe 3
              updateRes.getModifiedCount mustBe 3
            }
          }
        }

        "combine multiple updates together" in {
          withEmbeddedMongoDatabase { db =>
            val result = for {
              coll <- db.getCollection("coll")
              _    <- coll.insertMany(TestData.accounts)
              updateQuery = Update
                .set("status", "updated")
                .combinedWith(Update.rename("currency", "money"))
                .combinedWith(Update.unset("name"))
              updateResult <- coll.updateMany(Filter.empty, updateQuery)
              docs         <- coll.find.all
            } yield (updateResult, docs)

            result.map { case (updateRes, docs) =>
              docs must have size 3
              docs.flatMap(_.getString("status")).toSet mustBe Set("updated")
              docs.forall(_.contains("money")) mustBe true
              docs.forall(!_.contains("currency")) mustBe true
              docs.forall(!_.contains("currency")) mustBe true
              updateRes.getMatchedCount mustBe 3
              updateRes.getModifiedCount mustBe 3
            }
          }
        }
      }

      "deleteOne and deleteMany" should {
        "delete one doc in coll" in {
          withEmbeddedMongoDatabase { db =>
            val result = for {
              coll      <- db.getCollection("coll")
              _         <- coll.insertMany(TestData.accounts)
              deleteRes <- coll.deleteOne(Filter.idEq(TestData.eurAccount.getObjectId("_id")))
              docs      <- coll.find.all
            } yield (deleteRes, docs)

            result.map { case (deleteRes, docs) =>
              docs must have size 2
              deleteRes.getDeletedCount mustBe 1
            }
          }
        }

        "delete many docs in coll" in {
          withEmbeddedMongoDatabase { db =>
            val result = for {
              coll      <- db.getCollection("coll")
              _         <- coll.insertMany(TestData.accounts)
              deleteRes <- coll.deleteMany(Filter.eq("currency", TestData.EUR).not)
              docs      <- coll.find.all
            } yield (deleteRes, docs)

            result.map { case (deleteRes, docs) =>
              docs mustBe List(TestData.eurAccount)
              deleteRes.getDeletedCount mustBe 2
            }
          }
        }
      }

      "findOneAndReplace" should {
        "find and replace doc in coll" in {
          withEmbeddedMongoDatabase { db =>
            val result = for {
              coll <- db.getCollection("coll")
              _    <- coll.insertOne(TestData.eurAccount)
              old  <- coll.findOneAndReplace(Filter.eq("currency", TestData.EUR), Document("currency" := TestData.GBP))
              docs <- coll.find.all
            } yield (old, docs)

            result.map { case (old, docs) =>
              docs.flatMap(_.getDocument("currency")) mustBe List(TestData.GBP)
              old mustBe Some(TestData.eurAccount)
            }
          }
        }
      }

      "findOneAndUpdate" should {
        "find and update doc in coll" in {
          withEmbeddedMongoDatabase { db =>
            val result = for {
              coll <- db.getCollection("coll")
              _    <- coll.insertMany(TestData.accounts)
              old  <- coll.findOneAndUpdate(Filter.eq("currency", TestData.EUR), Update.set("status", "updated"))
              docs <- coll.find.filter(Filter.exists("status")).all
            } yield (old, docs)

            result.map { case (old, docs) =>
              old mustBe Some(TestData.eurAccount)
              docs mustBe List(TestData.eurAccount += ("status" -> "updated"))
            }
          }
        }
      }

      "findOneAndDelete" should {
        "find and delete doc in coll" in {
          withEmbeddedMongoDatabase { db =>
            val result = for {
              coll <- db.getCollection("coll")
              _    <- coll.insertMany(TestData.accounts)
              old  <- coll.findOneAndDelete(Filter.eq("name", "eur-acc"))
              docs <- coll.find.all
            } yield (old, docs)

            result.map { case (old, docs) =>
              docs must have size 2
              docs.flatMap(_.getString("name")).toSet mustBe Set("gbp-acc", "usd-acc")
              old.flatMap(_.getString("name")) mustBe Some("eur-acc")
            }
          }
        }
      }

      "find" should {
        "find docs by field" in {
          withEmbeddedMongoDatabase { db =>
            val result = for {
              coll <- db.getCollection("coll")
              _    <- coll.insertMany(TestData.accounts)
              res  <- coll.find.filter(Filter.eq("currency", TestData.EUR)).all
            } yield res

            result.map { res =>
              res mustBe List(TestData.eurAccount)
            }
          }
        }

        "get all docs with sort, skip and limit" in {
          withEmbeddedMongoDatabase { db =>
            val result = for {
              coll <- db.getCollection("coll")
              _    <- coll.insertMany(TestData.categories)
              res  <- coll.find.sortByDesc("name").skip(2).limit(3).all
            } yield res

            result.map { found =>
              found.flatMap(_.getString("name")) mustBe List("cat-7", "cat-6", "cat-5")
            }
          }
        }

        "get first doc with sort, skip and limit" in {
          withEmbeddedMongoDatabase { db =>
            val result = for {
              coll <- db.getCollection("coll")
              _    <- coll.insertMany(TestData.categories)
              res  <- coll.find.sort(Sort.desc("name")).skip(3).limit(2).first
            } yield res

            result.map(_.flatMap(_.getString("name")) mustBe Some("cat-6"))
          }
        }

        "return none when there are no docs that match query" in {
          withEmbeddedMongoDatabase { db =>
            val result = for {
              coll <- db.getCollection("coll")
              res  <- coll.find.sort(Sort.desc("name")).skip(3).limit(2).first
            } yield res

            result.map(_ mustBe None)
          }
        }

        "stream with filter" in {
          withEmbeddedMongoDatabase { db =>
            val result = for {
              coll <- db.getCollection("coll")
              _    <- coll.insertMany(TestData.categories(50000))
              res  <- coll.find.filter(Filter.regex("name", "cat-(1|3|5).*")).stream.compile.toList
            } yield res

            result.map(_ must have size 23333)
          }
        }

        "bounded stream" in {
          withEmbeddedMongoDatabase { db =>
            val result = for {
              cats <- db.getCollection("categories")
              txs  <- db.getCollection("transactions")
              _    <- (cats.insertMany(TestData.categories), txs.insertMany(TestData.transactions(1000000))).parTupled
              res  <- txs.find.boundedStream(100).compile.count
            } yield res

            result.map(_ mustBe 1000000)
          }
        }

        "execute multiple bounded streams in parallel" in {
          withEmbeddedMongoDatabase { db =>
            val result = for {
              cats <- db.getCollection("categories")
              txs  <- db.getCollection("transactions")
              _    <- cats.insertMany(TestData.categories)
              _    <- txs.insertMany(TestData.transactions(1000000))
              res <- Stream(
                txs.find.skip(10000).limit(10000).boundedStream(100),
                txs.find.skip(20000).limit(10000).boundedStream(100),
                txs.find.skip(30000).limit(10000).boundedStream(100),
                txs.find.skip(40000).limit(10000).boundedStream(100),
                txs.find.skip(50000).limit(10000).boundedStream(100)
              ).parJoinUnbounded.compile.count
            } yield res

            result.map(_ mustBe 50000)
          }
        }
      }

      "distinct" should {
        "return distinct fields of a doc" in {
          withEmbeddedMongoDatabase { db =>
            val result = for {
              coll <- db.getCollection("coll")
              _    <- coll.insertMany(TestData.accounts)
              res  <- coll.distinct[Document]("currency").all
            } yield res

            result.map { res =>
              res.toSet mustBe Set(TestData.USD, TestData.EUR, TestData.GBP)
            }
          }
        }
      }

      "bulkWrite" should {
        "perform multiple operations at once" in {
          withEmbeddedMongoDatabase { db =>
            val result = for {
              coll <- db.getCollection("coll")
              _    <- coll.insertMany(TestData.accounts)
              res <- coll.bulkWrite(
                List(
                  WriteCommand.InsertOne(TestData.lvlAccount),
                  WriteCommand.DeleteOne(Filter.eq("name", "eur-acc")),
                  WriteCommand.UpdateOne(Filter.eq("name", "gbp-acc"), Update.set("foo", "bar"))
                )
              )
            } yield res

            result.map { res =>
              res.getDeletedCount mustBe 1
              res.getModifiedCount mustBe 1
              res.getInsertedCount mustBe 1
            }
          }
        }
      }

      "renameCollection" should {
        "change collection name and keep the data" in {
          withEmbeddedMongoDatabase { db =>
            val result = for {
              coll  <- db.getCollection("coll")
              _     <- coll.insertMany(TestData.accounts)
              _     <- coll.renameCollection(new MongoNamespace("db.coll2"), RenameCollectionOptions(dropTarget = false))
              coll2 <- db.getCollection("coll2")
              count <- coll2.count
            } yield (count, coll2.namespace)

            result.map { case (count, ns) =>
              ns.getCollectionName mustBe "coll2"
              count mustBe 3
            }
          }
        }
      }

      "createIndex" should {
        "create index and check if it exists" in {
          val parameters = Table(
            ("index", "options", "expected"),
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
          )

          forAll(parameters) { (index, options, expected) =>
            withEmbeddedMongoDatabase { db =>
              val result = for {
                coll    <- db.getCollection("coll")
                _       <- coll.createIndex(index, options)
                indexes <- coll.listIndexes
              } yield indexes

              result.map { indexes =>
                indexes must contain theSameElementsAs expected.map(Document.parse)
              }
            }
          }
        }
      }
    }

    "working with json" should {

      "findOneAndUpdate" should {
        "find and update doc in coll" in {
          withEmbeddedMongoDatabase { db =>
            val json =
              """{
                |"firstName": "John",
                |"lastName": "Bloggs",
                |"dob": "1970-01-01"
                |}""".stripMargin

            val result = for {
              coll    <- db.getCollection("coll")
              _       <- coll.insertOne(Document.parse(json))
              old     <- coll.findOneAndUpdate(Filter.eq("lastName", "Bloggs"), Update.set("dob", "2020-01-01"))
              updated <- coll.find.first
            } yield (old, updated)

            result.map { case (old, updated) =>
              updated mustBe old.map(_ += "dob" -> "2020-01-01")
            }
          }
        }
      }
    }
  }

  def withEmbeddedMongoDatabase[A](test: MongoDatabase[IO] => IO[A]): Future[A] =
    withRunningEmbeddedMongo {
      MongoClient
        .fromConnectionString[IO](s"mongodb://localhost:$mongoPort")
        .use { client =>
          for {
            db    <- client.getDatabase("db")
            start <- IO.realTime
            res   <- test(db)
            end   <- IO.realTime
            duration = end - start
            _ <- IO.println(s">>>> test duration ${duration.toMillis}ms")
          } yield res
        }
    }.unsafeToFuture()(IORuntime.global)

}
