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

package mongo4cats.client

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.mongodb.{ClientBulkWriteException, MongoClientException, WriteConcern}
import mongo4cats.bson.Document
import mongo4cats.bson.syntax._
import mongo4cats.embedded.EmbeddedMongo
import mongo4cats.models.client._
import mongo4cats.models.collection.MongoNamespace
import mongo4cats.operations.{Filter, Sort, Update}
import mongo4cats.test.FreePort
import org.scalatest.Assertion
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import scala.concurrent.Future

class MongoClientBulkWriteSpec extends AsyncWordSpec with Matchers with EmbeddedMongo {
  private val people = MongoNamespace("first", "people")
  private val logs = MongoNamespace("first", "logs")
  private val otherPeople = MongoNamespace("second", "people")

  private def withClient(test: MongoClient[IO] => IO[Assertion]): Future[Assertion] = {
    val port = FreePort.next()
    withRunningEmbeddedMongo(port) {
      MongoClient.fromConnectionString[IO](s"mongodb://localhost:$port").use(test)
    }.unsafeToFuture()
  }

  "Client bulk writes" should {
    "execute every command variant across collections and databases with indexed verbose results" in withClient { client =>
      for {
        seed <- client.bulkWrite(List(
          ClientWriteCommand.InsertOne(people, Document("_id" := 1, "n" := 0)),
          ClientWriteCommand.InsertOne(people, Document("_id" := 2, "n" := 0)),
          ClientWriteCommand.InsertOne(logs, Document("_id" := 3)),
          ClientWriteCommand.InsertOne(otherPeople, Document("_id" := 4, "n" := 0)),
          ClientWriteCommand.InsertOne(otherPeople, Document("_id" := 5, "n" := 0))
        ))
        result <- client.bulkWrite(List(
          ClientWriteCommand.InsertOne(people, Document("_id" := 6, "n" := 0)),
          ClientWriteCommand.UpdateOne(people, Filter.eq("_id", 1), Update.inc("n", 1)),
          ClientWriteCommand.UpdateMany(otherPeople, Filter.empty, Update.inc("n", 2)),
          ClientWriteCommand.ReplaceOne(people, Filter.eq("_id", 2), Document("_id" := 2, "n" := 10)),
          ClientWriteCommand.PipelinedUpdateOne(people, Filter.eq("_id", 6), List(Document("$set" := Document("n" := 3)))),
          ClientWriteCommand.PipelinedUpdateMany(otherPeople, Filter.empty, List(Document("$set" := Document("done" := true)))),
          ClientWriteCommand.DeleteOne(logs, Filter.eq("_id", 3)),
          ClientWriteCommand.DeleteMany(otherPeople, Filter.eq("done", true))
        ), ClientBulkWriteOptions(verboseResults = true))
        db1 <- client.getDatabase("first")
        coll <- db1.getCollection("people")
        remaining <- coll.find.sort(Sort.asc("_id")).all
        logColl <- db1.getCollection("logs")
        logCount <- logColl.count
        db2 <- client.getDatabase("second")
        other <- db2.getCollection("people")
        otherCount <- other.count
      } yield {
        seed.getInsertedCount mustBe 5L
        seed.getVerboseResults.isPresent mustBe false
        result.isAcknowledged mustBe true
        result.getInsertedCount mustBe 1L
        result.getMatchedCount mustBe 7L
        result.getModifiedCount mustBe 7L
        result.getDeletedCount mustBe 3L
        result.getUpsertedCount mustBe 0L
        val verbose = result.getVerboseResults.get()
        verbose.getInsertResults.size mustBe 1
        verbose.getInsertResults.get(0).getInsertedId.get().asInt32.getValue mustBe 6
        verbose.getUpdateResults.size mustBe 5
        verbose.getUpdateResults.get(2).getModifiedCount mustBe 2L
        verbose.getDeleteResults.size mustBe 2
        verbose.getDeleteResults.get(7).getDeletedCount mustBe 2L
        remaining.toList.flatMap(_.getInt("n")) mustBe List(1, 10, 3)
        logCount mustBe 0L
        otherCount mustBe 0L
      }
    }

    "apply upsert options and bulk let variables to pipeline updates" in withClient { client =>
      for {
        result <- client.bulkWrite(List(
          ClientWriteCommand.UpdateOne(people, Filter.eq("_id", 10), Update.set("n", 1), ClientUpdateOneOptions(upsert = true)),
          ClientWriteCommand.ReplaceOne(otherPeople, Filter.eq("_id", 11), Document("_id" := 11, "n" := 2), ClientReplaceOneOptions(upsert = true)),
          ClientWriteCommand.PipelinedUpdateOne(people, Filter.eq("_id", 10), List(Document("$set" := Document("n" := "$$number"))))
        ), ClientBulkWriteOptions(verboseResults = true, comment = Some("client bulk test"), let = Some(Document("number" := 42))))
        db <- client.getDatabase("first")
        coll <- db.getCollection("people")
        stored <- coll.find.first
      } yield {
        result.getUpsertedCount mustBe 2L
        result.getModifiedCount mustBe 1L
        result.getVerboseResults.get().getUpdateResults.get(0).getUpsertedId.get().asInt32.getValue mustBe 10
        stored.flatMap(_.getInt("n")) mustBe Some(42)
      }
    }

    List(true, false).foreach { ordered =>
      s"preserve partial results and original error indexes when ordered=$ordered" in withClient { client =>
        for {
          _ <- client.bulkWrite(List(ClientWriteCommand.InsertOne(people, Document("_id" := 1))))
          attempted <- client.bulkWrite(List(
            ClientWriteCommand.InsertOne(people, Document("_id" := 2)),
            ClientWriteCommand.InsertOne(people, Document("_id" := 1)),
            ClientWriteCommand.InsertOne(otherPeople, Document("_id" := 3))
          ), ClientBulkWriteOptions(ordered = ordered, verboseResults = true)).attempt
          db <- client.getDatabase("second")
          coll <- db.getCollection("people")
          count <- coll.count
        } yield attempted match {
          case Left(error: ClientBulkWriteException) =>
            error.getWriteErrors.size mustBe 1
            error.getWriteErrors.get(1).getCode mustBe 11000
            error.getWriteConcernErrors.isEmpty mustBe true
            val partial = error.getPartialResult.get()
            partial.getInsertedCount mustBe (if (ordered) 1L else 2L)
            val inserts = partial.getVerboseResults.get().getInsertResults
            inserts.containsKey(0) mustBe true
            inserts.containsKey(1) mustBe false
            inserts.containsKey(2) mustBe !ordered
            count mustBe (if (ordered) 0L else 1L)
          case other => fail(s"Expected a client bulk write exception, got $other")
        }
      }
    }

    "report no partial result when the first ordered write fails" in withClient { client =>
      for {
        _ <- client.bulkWrite(List(ClientWriteCommand.InsertOne(people, Document("_id" := 1))))
        attempted <- client.bulkWrite(List(
          ClientWriteCommand.InsertOne(people, Document("_id" := 1)),
          ClientWriteCommand.InsertOne(otherPeople, Document("_id" := 2))
        )).attempt
      } yield attempted match {
        case Left(error: ClientBulkWriteException) =>
          error.getWriteErrors.get(0).getCode mustBe 11000
          error.getPartialResult.isPresent mustBe false
        case other => fail(s"Expected a client bulk write exception, got $other")
      }
    }

    "encode heterogeneous inserts and replacements using the client registry" in {
      val port = FreePort.next()
      val settings = MongoClientSettings.builder(codecRegistry = ClientBulkWriteFixture.registry)
        .applyConnectionString(ConnectionString(s"mongodb://localhost:$port")).build()
      withRunningEmbeddedMongo(port) {
        MongoClient.create[IO](settings).use { client =>
          for {
            result <- client.bulkWrite(List(
              ClientWriteCommand.InsertOne(people, ClientBulkWriteFixture.Record(1, "before")),
              ClientWriteCommand.InsertOne(otherPeople, Document("_id" := 2, "name" := "document")),
              ClientWriteCommand.ReplaceOne(people, Filter.eq("_id", 1), ClientBulkWriteFixture.Record(1, "after"))
            ))
            db <- client.getDatabase("first")
            coll <- db.getCollection("people")
            stored <- coll.find.first
            db2 <- client.getDatabase("second")
            coll2 <- db2.getCollection("people")
            other <- coll2.find.first
          } yield {
            result.getInsertedCount mustBe 2L
            result.getModifiedCount mustBe 1L
            stored.flatMap(_.getString("name")) mustBe Some("after")
            other.flatMap(_.getString("name")) mustBe Some("document")
          }
        }
      }.unsafeToFuture()
    }

    "support unacknowledged results and reject incompatible options inside the effect" in {
      val port = FreePort.next()
      val settings = MongoClientSettings.builder(writeConcern = WriteConcern.UNACKNOWLEDGED)
        .applyConnectionString(ConnectionString(s"mongodb://localhost:$port")).build()
      withRunningEmbeddedMongo(port) {
        MongoClient.create[IO](settings).use { client =>
          val commands = List(ClientWriteCommand.InsertOne(people, Document("_id" := 1)))
          for {
            result <- client.bulkWrite(commands, ClientBulkWriteOptions(ordered = false))
            ordered <- client.bulkWrite(commands).attempt
            verbose <- client.bulkWrite(commands, ClientBulkWriteOptions(ordered = false, verboseResults = true)).attempt
          } yield {
            result.isAcknowledged mustBe false
            ordered.swap.toOption.get mustBe a[MongoClientException]
            verbose.swap.toOption.get mustBe a[MongoClientException]
          }
        }
      }.unsafeToFuture()
    }

    "reject an empty batch and invalid command through the effect" in withClient { client =>
      val empty = client.bulkWrite(Nil)
      val invalid = client.bulkWrite(List(ClientWriteCommand.InsertOne(people, null: Document)))
      for {
        emptyResult <- empty.attempt
        invalidResult <- invalid.attempt
      } yield {
        emptyResult.swap.toOption.get mustBe a[IllegalArgumentException]
        invalidResult.swap.toOption.get mustBe a[IllegalArgumentException]
      }
    }
  }
}
