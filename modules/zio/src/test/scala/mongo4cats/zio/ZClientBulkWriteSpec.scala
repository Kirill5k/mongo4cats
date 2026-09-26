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

import com.mongodb.{ClientBulkWriteException, MongoClientException, WriteConcern}
import mongo4cats.bson.Document
import mongo4cats.bson.syntax._
import mongo4cats.client.ClientBulkWriteFixture
import mongo4cats.models.client._
import mongo4cats.models.collection.MongoNamespace
import mongo4cats.operations.{Filter, Update}
import mongo4cats.test.FreePort
import mongo4cats.zio.embedded.EmbeddedMongo
import zio.{durationInt, RIO, Scope, ZIO}
import zio.test._
import zio.test.TestAspect.{sequential, timeout, withLiveClock}

object ZClientBulkWriteSpec extends ZIOSpecDefault with EmbeddedMongo {
  private val first  = MongoNamespace("first-db", "items")
  private val second = MongoNamespace("second-db", "items")
  private val audit  = MongoNamespace("first-db", "audit")

  override def spec: Spec[TestEnvironment with Scope, Any] = suite("Client bulk writes")(
    test("execute all command variants across collections and databases with indexed verbose results") {
      withClient() { client =>
        val commands = List(
          ClientWriteCommand.InsertOne(first, Document("_id" := 1, "score" := 0)),
          ClientWriteCommand.InsertOne(first, Document("_id" := 2, "score" := 0)),
          ClientWriteCommand.InsertOne(first, Document("_id" := 3, "score" := 0)),
          ClientWriteCommand.InsertOne(second, Document("_id" := 1, "score" := 0)),
          ClientWriteCommand.InsertOne(second, Document("_id" := 2, "score" := 0)),
          ClientWriteCommand.InsertOne(second, Document("_id" := 3, "score" := 0)),
          ClientWriteCommand.InsertOne(audit, Document("_id" := 1)),
          ClientWriteCommand.UpdateOne(first, Filter.eq("_id", 1), Update.set("score", 10)),
          ClientWriteCommand.UpdateMany(first, Filter.gte("_id", 2), Update.set("score", 20)),
          ClientWriteCommand.PipelinedUpdateOne(second, Filter.eq("_id", 1), List(Update.set("score", 30).toBson)),
          ClientWriteCommand.PipelinedUpdateMany(second, Filter.gte("_id", 2), List(Update.set("score", 40).toBson)),
          ClientWriteCommand.ReplaceOne(first, Filter.eq("_id", 1), Document("_id" := 1, "score" := 50)),
          ClientWriteCommand.DeleteOne(first, Filter.eq("_id", 2)),
          ClientWriteCommand.DeleteMany(second, Filter.gte("_id", 2))
        )
        for {
          result <- client.bulkWrite(commands, ClientBulkWriteOptions(verboseResults = true))
          firstDocs <- documents(client, first)
          secondDocs <- documents(client, second)
          auditDocs <- documents(client, audit)
        } yield {
          val verbose = result.getVerboseResults.get()
          assertTrue(
            result.isAcknowledged,
            result.getInsertedCount == 7L,
            result.getMatchedCount == 7L,
            result.getModifiedCount == 7L,
            result.getDeletedCount == 3L,
            result.getUpsertedCount == 0L,
            verbose.getInsertResults.size() == 7,
            verbose.getUpdateResults.size() == 5,
            verbose.getUpdateResults.get(8).getMatchedCount == 2L,
            verbose.getDeleteResults.get(13).getDeletedCount == 2L,
            firstDocs.toSet == Set(Document("_id" := 1, "score" := 50), Document("_id" := 3, "score" := 20)),
            secondDocs.toList == List(Document("_id" := 1, "score" := 30)),
            auditDocs.toList == List(Document("_id" := 1))
          )
        }
      }
    },
    test("support update, replacement and pipeline upserts") {
      withClient() { client =>
        for {
          result <- client.bulkWrite(
            List(
              ClientWriteCommand.UpdateOne(first, Filter.eq("_id", 1), Update.set("kind", "one"), ClientUpdateOneOptions(upsert = true)),
              ClientWriteCommand.UpdateMany(first, Filter.eq("_id", 2), Update.set("kind", "many"), ClientUpdateManyOptions(upsert = true)),
              ClientWriteCommand.ReplaceOne(second, Filter.eq("_id", 3), Document("_id" := 3), ClientReplaceOneOptions(upsert = true)),
              ClientWriteCommand.PipelinedUpdateOne(
                second,
                Filter.eq("_id", 4),
                List(Update.set("kind", "pipeline-one").toBson),
                ClientUpdateOneOptions(upsert = true)
              ),
              ClientWriteCommand.PipelinedUpdateMany(
                second,
                Filter.eq("_id", 5),
                List(Update.set("kind", "pipeline-many").toBson),
                ClientUpdateManyOptions(upsert = true)
              )
            ),
            ClientBulkWriteOptions(verboseResults = true)
          )
          firstDocs <- documents(client, first)
          secondDocs <- documents(client, second)
        } yield assertTrue(
          result.getInsertedCount == 0L,
          result.getMatchedCount == 0L,
          result.getModifiedCount == 0L,
          result.getUpsertedCount == 5L,
          result.getVerboseResults.get().getUpdateResults.size() == 5,
          result.getVerboseResults.get().getUpdateResults.get(4).getUpsertedId.get().asInt32().getValue == 5,
          firstDocs.size == 2,
          secondDocs.size == 3
        )
      }
    },
    test("use the client codec registry for heterogeneous documents and omit verbose results by default") {
      withClient(Some(ClientBulkWriteFixture.registry)) { client =>
        for {
          result <- client.bulkWrite(
            List(
              ClientWriteCommand.InsertOne(first, ClientBulkWriteFixture.Record(1, "before")),
              ClientWriteCommand.InsertOne(second, Document("_id" := 2, "name" := "document")),
              ClientWriteCommand.ReplaceOne(first, Filter.eq("_id", 1), ClientBulkWriteFixture.Record(1, "after"))
            )
          )
          firstDocs <- documents(client, first)
          secondDocs <- documents(client, second)
        } yield assertTrue(
          result.getInsertedCount == 2L,
          result.getModifiedCount == 1L,
          !result.getVerboseResults.isPresent,
          firstDocs.toList == List(Document("_id" := 1, "name" := "after")),
          secondDocs.toList == List(Document("_id" := 2, "name" := "document"))
        )
      }
    },
    test("stop ordered writes after an error and retain successful partial results") {
      withClient() { client =>
        for {
          result <- client.bulkWrite(
            List(
              ClientWriteCommand.InsertOne(first, Document("_id" := 1)),
              ClientWriteCommand.InsertOne(first, Document("_id" := 1)),
              ClientWriteCommand.InsertOne(second, Document("_id" := 2))
            ),
            ClientBulkWriteOptions(verboseResults = true)
          ).either
          firstDocs <- documents(client, first)
          secondDocs <- documents(client, second)
        } yield result match {
          case Left(error: ClientBulkWriteException) =>
            val partial = error.getPartialResult.get()
            assertTrue(
              error.getWriteErrors.size() == 1,
              error.getWriteErrors.get(1).getCode == 11000,
              partial.getInsertedCount == 1L,
              partial.getVerboseResults.get().getInsertResults.containsKey(0),
              partial.getVerboseResults.get().getInsertResults.size() == 1,
              firstDocs.size == 1,
              secondDocs.isEmpty
            )
          case _ => assertTrue(false)
        }
      }
    },
    test("continue unordered writes and preserve original operation indexes for errors and successes") {
      withClient() { client =>
        for {
          _ <- client.bulkWrite(
            List(
              ClientWriteCommand.InsertOne(first, Document("_id" := 1)),
              ClientWriteCommand.InsertOne(second, Document("_id" := 2))
            )
          )
          result <- client.bulkWrite(
            List(
              ClientWriteCommand.InsertOne(first, Document("_id" := 3)),
              ClientWriteCommand.InsertOne(first, Document("_id" := 1)),
              ClientWriteCommand.InsertOne(second, Document("_id" := 4)),
              ClientWriteCommand.InsertOne(second, Document("_id" := 2))
            ),
            ClientBulkWriteOptions(ordered = false, verboseResults = true)
          ).either
          firstDocs <- documents(client, first)
          secondDocs <- documents(client, second)
        } yield result match {
          case Left(error: ClientBulkWriteException) =>
            val partial = error.getPartialResult.get()
            val inserts = partial.getVerboseResults.get().getInsertResults
            assertTrue(
              error.getWriteErrors.size() == 2,
              error.getWriteErrors.get(1).getCode == 11000,
              error.getWriteErrors.get(3).getCode == 11000,
              partial.getInsertedCount == 2L,
              inserts.size() == 2,
              inserts.containsKey(0),
              inserts.containsKey(2),
              firstDocs.size == 2,
              secondDocs.size == 2
            )
          case _ => assertTrue(false)
        }
      }
    },
    test("return no partial result when the first ordered operation fails") {
      withClient() { client =>
        for {
          _ <- client.bulkWrite(List(ClientWriteCommand.InsertOne(first, Document("_id" := 1))))
          result <- client.bulkWrite(
            List(
              ClientWriteCommand.InsertOne(first, Document("_id" := 1)),
              ClientWriteCommand.InsertOne(second, Document("_id" := 2))
            )
          ).either
          secondDocs <- documents(client, second)
        } yield result match {
          case Left(error: ClientBulkWriteException) =>
            assertTrue(!error.getPartialResult.isPresent, error.getWriteErrors.containsKey(0), secondDocs.isEmpty)
          case _ => assertTrue(false)
        }
      }
    },
    test("reject an empty batch inside the effect") {
      withClient() { client =>
        client.bulkWrite(List.empty[ClientWriteCommand]).either.map(result => assertTrue(result.left.exists(_.isInstanceOf[IllegalArgumentException])))
      }
    },
    test("support unacknowledged unordered writes and reject incompatible options inside the effect") {
      withClient(writeConcern = WriteConcern.UNACKNOWLEDGED) { client =>
        val writes = List(ClientWriteCommand.InsertOne(first, Document("_id" := 1)))
        for {
          result <- client.bulkWrite(writes, ClientBulkWriteOptions(ordered = false))
          ordered <- client.bulkWrite(writes).either
          verbose <- client.bulkWrite(writes, ClientBulkWriteOptions(ordered = false, verboseResults = true)).either
        } yield assertTrue(
          !result.isAcknowledged,
          ordered.left.exists(_.isInstanceOf[MongoClientException]),
          verbose.left.exists(_.isInstanceOf[MongoClientException])
        )
      }
    }
  ) @@ sequential @@ withLiveClock @@ timeout(2.minutes)

  private def withClient[A](
      registry: Option[mongo4cats.codecs.CodecRegistry] = None,
      writeConcern: WriteConcern = WriteConcern.ACKNOWLEDGED
  )(run: ZMongoClient => RIO[Scope, A]): RIO[Scope, A] =
    ZIO.succeed(FreePort.next()).flatMap { port =>
      withRunningEmbeddedMongo(port) {
        val settings = MongoClientSettings
          .builder(writeConcern = writeConcern, codecRegistry = registry.getOrElse(mongo4cats.codecs.CodecRegistry.Default))
          .applyConnectionString(ConnectionString(s"mongodb://localhost:$port"))
          .build()
        ZMongoClient.create(settings).flatMap(run)
      }
    }

  private def documents(client: ZMongoClient, namespace: MongoNamespace): zio.Task[Iterable[Document]] =
    for {
      database <- client.getDatabase(namespace.databaseName)
      collection <- database.getCollection(namespace.collectionName)
      result <- collection.find.all
    } yield result
}
