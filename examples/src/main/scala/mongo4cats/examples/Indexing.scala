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
import mongo4cats.client.MongoClient
import mongo4cats.models.collection.IndexOptions
import mongo4cats.operations.Index

object Indexing extends IOApp.Simple {

  override val run: IO[Unit] =
    MongoClient.fromConnectionString[IO]("mongodb://localhost:27017").use { client =>
      for {
        db      <- client.getDatabase("testdb")
        coll    <- db.getCollection("indexes")
        _       <- coll.createIndex(Index.ascending(List("name", "email")), IndexOptions().unique(true))
        indexes <- coll.listIndexes
        _       <- IO.println(indexes)
      } yield ()
    }
}
