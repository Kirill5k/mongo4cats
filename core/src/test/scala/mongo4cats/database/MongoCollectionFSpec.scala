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
import com.mongodb.client.model.{Filters, Sorts, Updates}
import mongo4cats.{EmbeddedMongo}
import mongo4cats.bson.Document
import mongo4cats.client.MongoClientF
import mongo4cats.database.operations.{Filter, Update}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import scala.concurrent.Future

class MongoCollectionFSpec extends AsyncWordSpec with Matchers with EmbeddedMongo {

  override val mongoPort = 12347

  "A MongoCollectionF" when {

    "working with Documents" should {
      "insertOne" should {
        "store new document in db" in {
          withEmbeddedMongoDatabase { db =>
            val result = for {
              coll         <- db.getCollection("coll")
              insertResult <- coll.insertOne[IO](document())
              documents    <- coll.find.all[IO]
            } yield (insertResult, documents)

            result.map { case (insertRes, documents) =>
              documents must have size 1
              documents.head.getString("name") mustBe "test-doc-1"
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
              insertResult <- coll.insertMany[IO](List(document(), document("test-doc-2")))
              documents    <- coll.find.all[IO]
            } yield (insertResult, documents)

            result.map { case (insertRes, documents) =>
              documents must have size 2
              documents.map(_.getString("name")) mustBe List("test-doc-1", "test-doc-2")
              insertRes.wasAcknowledged() mustBe true
              insertRes.getInsertedIds() must have size 2
            }
          }
        }
      }

      "count" should {
        "return count of all documents in collection" in {
          withEmbeddedMongoDatabase { db =>
            val result = for {
              coll  <- db.getCollection("coll")
              _     <- coll.insertMany[IO](List(document(), document("test-doc-2"), document("test-doc-3")))
              count <- coll.count[IO]
            } yield count

            result.map(_ mustBe 3)
          }
        }

        "return 0 for empty collection" in {
          withEmbeddedMongoDatabase { db =>
            val result = for {
              coll  <- db.getCollection("coll")
              count <- coll.count[IO]
            } yield count

            result.map(_ mustBe 0)
          }
        }

        "apply filters" in {
          withEmbeddedMongoDatabase { db =>
            val result = for {
              coll  <- db.getCollection("coll")
              _     <- coll.insertMany[IO](List(document(), document("test-doc-2"), document("test-doc-3")))
              count <- coll.count[IO](Filters.eq("name", "test-doc-2"))
            } yield count

            result.map(_ mustBe 1)
          }
        }
      }

      "deleteMany" should {
        "delete multiple docs in coll" in {
          withEmbeddedMongoDatabase { db =>
            val result = for {
              coll         <- db.getCollection("coll")
              _            <- coll.insertMany[IO](List(document(), document(), document()))
              deleteResult <- coll.deleteMany[IO](Filters.eq("name", "test-doc-1"))
              count        <- coll.count[IO]
            } yield (deleteResult, count)

            result.map { case (deleteRes, count) =>
              count mustBe 0
              deleteRes.getDeletedCount mustBe 3
            }
          }
        }
      }

      "deleteOne" should {
        "delete one docs in coll" in {
          withEmbeddedMongoDatabase { db =>
            val result = for {
              coll         <- db.getCollection("coll")
              _            <- coll.insertMany[IO](List(document(), document(), document()))
              deleteResult <- coll.deleteOne[IO](Filters.eq("name", "test-doc-1"))
              count        <- coll.count[IO]
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
              coll         <- db.getCollection("coll")
              _            <- coll.insertMany[IO](List(document()))
              updateResult <- coll.replaceOne[IO](Filters.eq("name", "test-doc-1"), document("test-doc-2"))
              docs         <- coll.find.all[IO]
            } yield (updateResult, docs)

            result.map { case (updateRes, docs) =>
              docs must have size 1
              docs.head.getString("name") mustBe "test-doc-2"
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
              _            <- coll.insertMany[IO](List(document(), document(), document()))
              updateResult <- coll.updateOne[IO](Filter.eq("name", "test-doc-1"), Update.set("name", "test-doc-2"))
              docs         <- coll.find.all[IO]
            } yield (updateResult, docs)

            result.map { case (updateRes, docs) =>
              docs must have size 3
              docs.map(_.getString("name")) must contain allElementsOf List("test-doc-2", "test-doc-1")
              updateRes.getMatchedCount mustBe 1
              updateRes.getModifiedCount mustBe 1
            }

          }
        }

        "update many docs in coll" in {
          withEmbeddedMongoDatabase { db =>
            val result = for {
              coll         <- db.getCollection("coll")
              _            <- coll.insertMany[IO](List(document(), document(), document()))
              updateResult <- coll.updateMany[IO](Filters.eq("name", "test-doc-1"), Updates.set("name", "test-doc-2"))
              docs         <- coll.find.all[IO]
            } yield (updateResult, docs)

            result.map { case (updateRes, docs) =>
              docs must have size 3
              docs.map(_.getString("name")) must contain allElementsOf List("test-doc-2")
              updateRes.getMatchedCount mustBe 3
              updateRes.getModifiedCount mustBe 3
            }
          }
        }

        "update all docs in coll" in {
          withEmbeddedMongoDatabase { db =>
            val result = for {
              coll <- db.getCollection("coll")
              _    <- coll.insertMany[IO](List(document(), document(), document()))
              updateQuery = Update.set("test-field", 1).rename("name", "renamed").unset("info")
              updateResult <- coll.updateMany[IO](Filter.all, updateQuery)
              docs         <- coll.find.all[IO]
            } yield (updateResult, docs)

            result.map { case (updateRes, docs) =>
              docs must have size 3
              docs.map(_.getInteger("test-field")).toSet mustBe Set(1)
              docs.forall(_.containsKey("renamed")) mustBe true
              docs.forall(!_.containsKey("name")) mustBe true
              docs.forall(!_.containsKey("info")) mustBe true
              updateRes.getMatchedCount mustBe 3
              updateRes.getModifiedCount mustBe 3
            }
          }
        }

        "combine multiple updates together" in {
          withEmbeddedMongoDatabase { db =>
            val result = for {
              coll <- db.getCollection("coll")
              _    <- coll.insertMany[IO](List(document(), document(), document()))
              updateQuery = Update
                .set("test-field", 1)
                .combinedWith(Update.rename("name", "renamed"))
                .combinedWith(Update.unset("info"))
              updateResult <- coll.updateMany[IO](Filter.all, updateQuery)
              docs         <- coll.find.all[IO]
            } yield (updateResult, docs)

            result.map { case (updateRes, docs) =>
              docs must have size 3
              docs.map(_.getInteger("test-field")).toSet mustBe Set(1)
              docs.forall(_.containsKey("renamed")) mustBe true
              docs.forall(!_.containsKey("name")) mustBe true
              docs.forall(!_.containsKey("info")) mustBe true
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
              _         <- coll.insertMany[IO](List(document(), document(), document()))
              deleteRes <- coll.deleteOne[IO](Filters.eq("name", "test-doc-1"))
              docs      <- coll.find.all[IO]
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
              _         <- coll.insertMany[IO](List(document(), document(), document()))
              deleteRes <- coll.deleteMany[IO](Filters.eq("name", "test-doc-1"))
              docs      <- coll.find.all[IO]
            } yield (deleteRes, docs)

            result.map { case (deleteRes, docs) =>
              docs must have size 0
              deleteRes.getDeletedCount mustBe 3
            }
          }
        }
      }

      "findOneAndReplace" should {
        "find and replace doc in coll" in {
          withEmbeddedMongoDatabase { db =>
            val result = for {
              coll <- db.getCollection("coll")
              _    <- coll.insertMany[IO](List(document()))
              old  <- coll.findOneAndReplace[IO](Filters.eq("name", "test-doc-1"), document("test-doc-2"))
              docs <- coll.find.all[IO]
            } yield (old, docs)

            result.map { case (old, docs) =>
              docs must have size 1
              docs.head.getString("name") mustBe "test-doc-2"
              old.getString("name") mustBe "test-doc-1"
            }
          }
        }
      }

      "findOneAndUpdate" should {
        "find and update doc in coll" in {
          withEmbeddedMongoDatabase { db =>
            val result = for {
              coll <- db.getCollection("coll")
              _    <- coll.insertMany[IO](List(document()))
              old  <- coll.findOneAndUpdate[IO](Filters.eq("name", "test-doc-1"), Updates.set("name", "test-doc-2"))
              docs <- coll.find.all[IO]
            } yield (old, docs)

            result.map { case (old, docs) =>
              docs must have size 1
              docs.head.getString("name") mustBe "test-doc-2"
              old.getString("name") mustBe "test-doc-1"
            }
          }
        }
      }

      "findOneAndDelete" should {
        "find and delete doc in coll" in {
          withEmbeddedMongoDatabase { db =>
            val result = for {
              coll <- db.getCollection("coll")
              _    <- coll.insertMany[IO](List(document()))
              old  <- coll.findOneAndDelete[IO](Filters.eq("name", "test-doc-1"))
              docs <- coll.find.all[IO]
            } yield (old, docs)

            result.map { case (old, docs) =>
              docs must have size 0
              old.getString("name") mustBe "test-doc-1"
            }
          }
        }
      }

      "find" should {
        "find docs by field" in {
          withEmbeddedMongoDatabase { db =>
            val result = for {
              coll <- db.getCollection("coll")
              _    <- coll.insertMany[IO](List(document("d1"), document("d2"), document("d3"), document("d4")))
              res  <- coll.find.filter(Filters.eq("name", "d1")).all[IO]
            } yield res

            result.map { res =>
              res must have size 1
              res.head.getString("name") mustBe "d1"
            }
          }
        }

        "get all docs with sort, skip and limit" in {
          withEmbeddedMongoDatabase { db =>
            val result = for {
              coll <- db.getCollection("coll")
              _    <- coll.insertMany[IO](List(document("d1"), document("d2"), document("d3"), document("d4")))
              res  <- coll.find.sortByDesc("name").skip(1).limit(2).all[IO]
            } yield res

            result.map { found =>
              found must have size 2
              found.map(_.getString("name")) mustBe (List("d3", "d2"))
            }
          }
        }

        "get first doc with sort, skip and limit" in {
          withEmbeddedMongoDatabase { db =>
            val result = for {
              coll <- db.getCollection("coll")
              _    <- coll.insertMany[IO](List(document("d1"), document("d2"), document("d3"), document("d4")))
              res  <- coll.find.sort(Sorts.descending("name")).skip(1).limit(2).first[IO]
            } yield res

            result.map(_.getString("name") mustBe "d3")
          }
        }

        "stream with filter" in {
          withEmbeddedMongoDatabase { db =>
            val result = for {
              coll <- db.getCollection("coll")
              docs = (0 until 50000).map(i => document(s"d$i")).toList
              _   <- coll.insertMany[IO](docs)
              res <- coll.find.filter(Filters.regex("name", "d(1|3|5).*")).stream[IO].compile.toList
            } yield res

            result.map(_ must have size 23333)
          }
        }

        "bounded stream" in {
          withEmbeddedMongoDatabase { db =>
            val result = for {
              coll <- db.getCollection("coll")
              docs = (0 until 1000000).map(i => document(s"d$i")).toList
              _   <- coll.insertMany[IO](docs)
              res <- coll.find.boundedStream[IO](100).compile.count
            } yield res

            result.map(_ mustBe 1000000)
          }
        }
      }

      "distinct" should {
        "distinct fields of a doc" in {
          withEmbeddedMongoDatabase { db =>
            val result = for {
              coll <- db.getCollection("coll")
              _    <- coll.insertMany[IO](List(document("d1"), document("d2"), document("d3"), document("d4")))
              res  <- coll.distinct[Document]("info").all[IO]
            } yield res

            result.map { res =>
              res mustBe List(Document.fromJson(s"""{"x": 42, "y": 23}""""))
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
              _       <- coll.insertOne[IO](Document.fromJson(json))
              old     <- coll.findOneAndUpdate[IO](Filters.eq("lastName", "Bloggs"), Updates.set("dob", "2020-01-01"))
              updated <- coll.find.first[IO]
            } yield (old, updated)

            result.map { case (old, updated) =>
              old.getObjectId("_id") mustBe updated.getObjectId("_id")
              old.getString("lastName") mustBe updated.getString("lastName")
            }
          }
        }
      }
    }
  }

  def withEmbeddedMongoDatabase[A](test: MongoDatabaseF[IO] => IO[A]): Future[A] =
    withRunningEmbeddedMongo {
      MongoClientF
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
    }.unsafeToFuture()

  def document(name: String = "test-doc-1"): Document =
    Document(Map("name" -> name, "info" -> Document.fromJson(s"""{"x": 42, "y": 23}"""")))
}
