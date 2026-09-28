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

package mongo4cats.examples

import cats.effect.{IO, IOApp}
import cats.syntax.all._
import com.mongodb.{ReadConcern, ReadPreference, WriteConcern}
import fs2.Stream
import mongo4cats.bson.Document
import mongo4cats.bson.syntax._
import mongo4cats.client.MongoClient
import mongo4cats.database.MongoDatabase
import mongo4cats.models.collection.{ChangeStreamDocument, ReplaceOptions}
import mongo4cats.operations.{Aggregate, Filter}

/** Run against a replica set. Keep one active consumer for this checkpoint id, and make processing safe to repeat. */
object DurableWatch extends IOApp.Simple {

  def consume(database: MongoDatabase[IO])(process: ChangeStreamDocument[Document] => IO[Unit]): IO[Unit] =
    database.getCollection(DurableWatchCheckpoint.collectionName).flatMap { collection =>
      val checkpoints = collection
        .withReadPreference(ReadPreference.primary())
        .withReadConcern(ReadConcern.MAJORITY)
        .withWriteConcern(WriteConcern.MAJORITY.withJournal(true))
      val load = checkpoints.find(DurableWatchCheckpoint.filter).first.flatMap(_.traverse(record => IO(DurableWatchCheckpoint.token(record))))
      def save(token: Document): IO[Unit] =
        checkpoints.replaceOne(DurableWatchCheckpoint.filter, DurableWatchCheckpoint.record(token), ReplaceOptions(upsert = true)).void
      def open(token: Option[Document]): Stream[IO, ChangeStreamDocument[Document]] = {
        val query = database.watch(DurableWatchCheckpoint.pipeline)
        token.fold(query)(saved => query.resumeAfter(saved.toBsonDocument)).stream
      }
      runWithCheckpoint(load, open, save)(process)
    }

  // Loading is repeated on each invocation; a failed handler or checkpoint write terminates the consumer.
  private[examples] def runWithCheckpoint(
      load: IO[Option[Document]],
      open: Option[Document] => Stream[IO, ChangeStreamDocument[Document]],
      save: Document => IO[Unit]
  )(process: ChangeStreamDocument[Document] => IO[Unit]): IO[Unit] =
    load.flatMap(token => open(token).evalMap(event => process(event) *> save(event.resumeToken)).compile.drain)

  override val run: IO[Unit] =
    MongoClient.fromConnectionString[IO]("mongodb://localhost:27017").use { client =>
      client.getDatabase("my-db").flatMap { database =>
        // Printing illustrates a handler; replace it with your idempotent application operation.
        consume(database)(event => IO.println(s"Change: $event"))
      }
    }
}

private[examples] object DurableWatchCheckpoint {
  val collectionName: String = "_change_stream_checkpoints"
  val id: String             = "my-db-events-v1"
  val filter: Filter         = Filter.eq("_id", id)

  // Otherwise each saved checkpoint would itself trigger another event in this database watch.
  val pipeline: Aggregate = Aggregate.matchBy(Filter.ne("ns.coll", collectionName))

  def record(token: Document): Document = Document("_id" := id, "resumeToken" := token)

  // A malformed checkpoint must fail, rather than silently reopen the stream at the present time.
  def token(record: Document): Document =
    record.getDocument("resumeToken").getOrElse(throw new IllegalArgumentException("Checkpoint is missing its BSON resumeToken document"))
}
