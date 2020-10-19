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
import mongo4cats.EmbeddedMongo
import mongo4cats.client.MongoClientF
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.mongodb.scala.bson.{Document, ObjectId}
import org.mongodb.scala.model.Filters
import org.mongodb.scala.model.Updates
import org.mongodb.scala.model.Sorts
import org.mongodb.scala.bson.codecs.Macros._
import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}
import org.mongodb.scala.MongoClient.DEFAULT_CODEC_REGISTRY

import scala.concurrent.ExecutionContext

final case class PersonInfo(x: Int, y: Int)
final case class Person(_id: ObjectId, name: String, info: PersonInfo)

object Person {
  def apply(name: String, info: PersonInfo): Person =
    Person(new ObjectId(), name, info)
}

class MongoCollectionFSpec extends AnyWordSpec with Matchers with EmbeddedMongo {

  implicit val cs = IO.contextShift(ExecutionContext.global)

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

            val (insertRes, documents) = result.unsafeRunSync()

            documents must have size 1
            documents.head.getString("name") must be("test-doc-1")
            insertRes.wasAcknowledged() must be(true)
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

            val (insertRes, documents) = result.unsafeRunSync()

            documents must have size 2
            documents.map(_.getString("name")) must be(List("test-doc-1", "test-doc-2"))
            insertRes.wasAcknowledged() must be(true)
            insertRes.getInsertedIds() must have size 2
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

            result.unsafeRunSync() must be(3)
          }
        }

        "return 0 for empty collection" in {
          withEmbeddedMongoDatabase { db =>
            val result = for {
              coll  <- db.getCollection("coll")
              count <- coll.count[IO]
            } yield count

            result.unsafeRunSync() must be(0)
          }
        }

        "apply filters" in {
          withEmbeddedMongoDatabase { db =>
            val result = for {
              coll  <- db.getCollection("coll")
              _     <- coll.insertMany[IO](List(document(), document("test-doc-2"), document("test-doc-3")))
              count <- coll.count[IO](Filters.equal("name", "test-doc-2"))
            } yield count

            result.unsafeRunSync() must be(1)
          }
        }
      }

      "deleteMany" should {
        "delete multiple docs in coll" in {
          withEmbeddedMongoDatabase { db =>
            val result = for {
              coll         <- db.getCollection("coll")
              _            <- coll.insertMany[IO](List(document(), document(), document()))
              deleteResult <- coll.deleteMany[IO](Filters.equal("name", "test-doc-1"))
              count        <- coll.count[IO]
            } yield (deleteResult, count)

            val (deleteRes, count) = result.unsafeRunSync()

            count must be(0)
            deleteRes.getDeletedCount must be(3)
          }
        }
      }

      "deleteOne" should {
        "delete one docs in coll" in {
          withEmbeddedMongoDatabase { db =>
            val result = for {
              coll         <- db.getCollection("coll")
              _            <- coll.insertMany[IO](List(document(), document(), document()))
              deleteResult <- coll.deleteOne[IO](Filters.equal("name", "test-doc-1"))
              count        <- coll.count[IO]
            } yield (deleteResult, count)

            val (deleteRes, count) = result.unsafeRunSync()

            count must be(2)
            deleteRes.getDeletedCount must be(1)
          }
        }
      }

      "replaceOne" should {
        "replace doc in coll" in {
          withEmbeddedMongoDatabase { db =>
            val result = for {
              coll         <- db.getCollection("coll")
              _            <- coll.insertMany[IO](List(document()))
              updateResult <- coll.replaceOne[IO](Filters.equal("name", "test-doc-1"), document("test-doc-2"))
              docs         <- coll.find.all[IO]
            } yield (updateResult, docs)

            val (updateRes, docs) = result.unsafeRunSync()

            docs must have size 1
            docs.head.getString("name") must be("test-doc-2")
            updateRes.getMatchedCount must be(1)
            updateRes.getModifiedCount must be(1)
          }
        }
      }

      "updateOne and updateMany" should {
        "update one doc in coll" in {
          withEmbeddedMongoDatabase { db =>
            val result = for {
              coll         <- db.getCollection("coll")
              _            <- coll.insertMany[IO](List(document(), document(), document()))
              updateResult <- coll.updateOne[IO](Filters.equal("name", "test-doc-1"), Updates.set("name", "test-doc-2"))
              docs         <- coll.find.all[IO]
            } yield (updateResult, docs)

            val (updateRes, docs) = result.unsafeRunSync()

            docs must have size 3
            docs.map(_.getString("name")) must contain allElementsOf List("test-doc-2", "test-doc-1")
            updateRes.getMatchedCount must be(1)
            updateRes.getModifiedCount must be(1)
          }
        }

        "update many docs in coll" in {
          withEmbeddedMongoDatabase { db =>
            val result = for {
              coll         <- db.getCollection("coll")
              _            <- coll.insertMany[IO](List(document(), document(), document()))
              updateResult <- coll.updateMany[IO](Filters.equal("name", "test-doc-1"), Updates.set("name", "test-doc-2"))
              docs         <- coll.find.all[IO]
            } yield (updateResult, docs)

            val (updateRes, docs) = result.unsafeRunSync()

            docs must have size 3
            docs.map(_.getString("name")) must contain allElementsOf List("test-doc-2")
            updateRes.getMatchedCount must be(3)
            updateRes.getModifiedCount must be(3)
          }
        }
      }

      "deleteOne and deleteMany" should {
        "delete one doc in coll" in {
          withEmbeddedMongoDatabase { db =>
            val result = for {
              coll      <- db.getCollection("coll")
              _         <- coll.insertMany[IO](List(document(), document(), document()))
              deleteRes <- coll.deleteOne[IO](Filters.equal("name", "test-doc-1"))
              docs      <- coll.find.all[IO]
            } yield (deleteRes, docs)

            val (deleteRes, docs) = result.unsafeRunSync()

            docs must have size 2
            deleteRes.getDeletedCount must be(1)
          }
        }

        "delete many docs in coll" in {
          withEmbeddedMongoDatabase { db =>
            val result = for {
              coll      <- db.getCollection("coll")
              _         <- coll.insertMany[IO](List(document(), document(), document()))
              deleteRes <- coll.deleteMany[IO](Filters.equal("name", "test-doc-1"))
              docs      <- coll.find.all[IO]
            } yield (deleteRes, docs)

            val (deleteRes, docs) = result.unsafeRunSync()

            docs must have size 0
            deleteRes.getDeletedCount must be(3)
          }
        }
      }

      "findOneAndReplace" should {
        "find and replace doc in coll" in {
          withEmbeddedMongoDatabase { db =>
            val result = for {
              coll <- db.getCollection("coll")
              _    <- coll.insertMany[IO](List(document()))
              old  <- coll.findOneAndReplace[IO](Filters.equal("name", "test-doc-1"), document("test-doc-2"))
              docs <- coll.find.all[IO]
            } yield (old, docs)

            val (old, docs) = result.unsafeRunSync()

            docs must have size 1
            docs.head.getString("name") must be("test-doc-2")
            old.getString("name") must be("test-doc-1")
          }
        }
      }

      "findOneAndUpdate" should {
        "find and update doc in coll" in {
          withEmbeddedMongoDatabase { db =>
            val result = for {
              coll <- db.getCollection("coll")
              _    <- coll.insertMany[IO](List(document()))
              old  <- coll.findOneAndUpdate[IO](Filters.equal("name", "test-doc-1"), Updates.set("name", "test-doc-2"))
              docs <- coll.find.all[IO]
            } yield (old, docs)

            val (old, docs) = result.unsafeRunSync()

            docs must have size 1
            docs.head.getString("name") must be("test-doc-2")
            old.getString("name") must be("test-doc-1")
          }
        }
      }

      "findOneAndDelete" should {
        "find and delete doc in coll" in {
          withEmbeddedMongoDatabase { db =>
            val result = for {
              coll <- db.getCollection("coll")
              _    <- coll.insertMany[IO](List(document()))
              old  <- coll.findOneAndDelete[IO](Filters.equal("name", "test-doc-1"))
              docs <- coll.find.all[IO]
            } yield (old, docs)

            val (old, docs) = result.unsafeRunSync()

            docs must have size 0
            old.getString("name") must be("test-doc-1")
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

            val found = result.unsafeRunSync()

            found must have size 1
          }
        }

        "all with sort and limit" in {
          withEmbeddedMongoDatabase { db =>
            val result = for {
              coll <- db.getCollection("coll")
              _    <- coll.insertMany[IO](List(document("d1"), document("d2"), document("d3"), document("d4")))
              res  <- coll.find.sort(Sorts.descending("name")).limit(3).all[IO]
            } yield res

            val found = result.unsafeRunSync()

            found must have size 3
            found.map(_.getString("name")) must be(List("d4", "d3", "d2"))
          }
        }

        "first with sort and limit" in {
          withEmbeddedMongoDatabase { db =>
            val result = for {
              coll <- db.getCollection("coll")
              _    <- coll.insertMany[IO](List(document("d1"), document("d2"), document("d3"), document("d4")))
              res  <- coll.find.sort(Sorts.descending("name")).limit(3).first[IO]
            } yield res

            val found = result.unsafeRunSync()

            found.getString("name") must be("d4")
          }
        }

        "stream" in {
          withEmbeddedMongoDatabase { db =>
            val result = for {
              coll <- db.getCollection("coll")
              _    <- coll.insertMany[IO](List(document("d1"), document("d2"), document("d3"), document("d4")))
              res  <- coll.find.stream[IO].compile.toList
            } yield res

            val found = result.unsafeRunSync()

            found must have size 4
          }
        }
      }

      "distinct" should {
        "find distinct docs by field" in {
          withEmbeddedMongoDatabase { db =>
            val result = for {
              coll <- db.getCollection("coll")
              _    <- coll.insertMany[IO](List(document("d1"), document("d2"), document("d3"), document("d4")))
              res  <- coll.distinct("info").all[IO]
            } yield res

            val distinct = result.unsafeRunSync()

            distinct must have size 1
          }
        }
      }
    }

    "working with case classes" should {
      val personCodecRegistry = fromRegistries(
        fromProviders(classOf[Person]),
        fromProviders(classOf[PersonInfo]),
        DEFAULT_CODEC_REGISTRY
      )

      "insertOne" should {
        "store new person in db" in {
          withEmbeddedMongoDatabase { db =>
            val result = for {
              coll         <- db.getCollection[Person]("coll", personCodecRegistry)
              insertResult <- coll.insertOne[IO](person())
              people       <- coll.find.all[IO]
            } yield (insertResult, people)

            val (insertRes, people) = result.unsafeRunSync()

            people must have size 1
            people.head.name must be("test-person-1")
            insertRes.wasAcknowledged() must be(true)
          }
        }
      }
    }
  }

  def withEmbeddedMongoDatabase[A](test: MongoDatabaseF[IO] => A): A =
    withRunningEmbeddedMongo() {
      MongoClientF
        .fromConnectionString[IO]("mongodb://localhost:12345")
        .use { client =>
          client.getDatabase("db").flatMap(db => IO(test(db)))
        }
        .unsafeRunSync()
    }

  def document(name: String = "test-doc-1"): Document =
    Document("name" -> name, "info" -> Document("x" -> 203, "y" -> 102))

  def person(name: String = "test-person-1"): Person =
    Person(name, PersonInfo(203, 102))
}
