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

import com.mongodb.{ReadConcern, ReadPreference, WriteConcern}
import mongo4cats.bson.Document
import mongo4cats.models.collection.{ChangeStreamDocument, ReplaceOptions}
import mongo4cats.zio.{ZMongoClient, ZMongoDatabase}
import zio.{Console, Task, ZIO, ZIOAppDefault}
import zio.stream.ZStream

/** The ZIO version of DurableWatch, with the same checkpoint format and one-consumer requirement. */
object ZioDurableWatch extends ZIOAppDefault {

  def consume(database: ZMongoDatabase)(process: ChangeStreamDocument[Document] => Task[Unit]): Task[Unit] =
    database.getCollection(DurableWatchCheckpoint.collectionName).flatMap { collection =>
      val checkpoints = collection
        .withReadPreference(ReadPreference.primary())
        .withReadConcern(ReadConcern.MAJORITY)
        .withWriteConcern(WriteConcern.MAJORITY.withJournal(true))
      val load: Task[Option[Document]] = checkpoints.find(DurableWatchCheckpoint.filter).first.flatMap { record =>
        ZIO.attempt(record.map(DurableWatchCheckpoint.token))
      }
      def save(token: Document): Task[Unit] =
        checkpoints.replaceOne(DurableWatchCheckpoint.filter, DurableWatchCheckpoint.record(token), ReplaceOptions(upsert = true)).unit
      def open(token: Option[Document]): ZStream[Any, Throwable, ChangeStreamDocument[Document]] = {
        val query = database.watch(DurableWatchCheckpoint.pipeline)
        token.fold(query)(saved => query.resumeAfter(saved.toBsonDocument)).stream
      }
      runWithCheckpoint(load, open, save)(process)
    }

  private[examples] def runWithCheckpoint(
      load: Task[Option[Document]],
      open: Option[Document] => ZStream[Any, Throwable, ChangeStreamDocument[Document]],
      save: Document => Task[Unit]
  )(process: ChangeStreamDocument[Document] => Task[Unit]): Task[Unit] =
    load.flatMap(token => open(token).mapZIO(event => process(event) *> save(event.resumeToken)).runDrain)

  override val run: Task[Unit] =
    ZIO.scoped {
      ZMongoClient.fromConnectionString("mongodb://localhost:27017").flatMap { client =>
        client.getDatabase("my-db").flatMap { database =>
          consume(database)(event => Console.printLine(s"Change: $event"))
        }
      }
    }
}
