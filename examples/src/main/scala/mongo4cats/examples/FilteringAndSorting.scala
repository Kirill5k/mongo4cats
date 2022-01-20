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
import mongo4cats.bson.BsonDocument
import mongo4cats.bson.syntax._
import mongo4cats.collection.operations.Filter
import mongo4cats.embedded.EmbeddedMongo

object FilteringAndSorting extends IOApp.Simple with EmbeddedMongo {
  override val run: IO[Unit] =
    withRunningEmbeddedMongo("localhost", 27017) {
      MongoClient.fromConnectionString[IO]("mongodb://localhost:27017").use { client =>
        for {
          db <- client.getDatabase("testdb")
          coll <- db.getCollection("docs")
          _ <- coll.insertMany[BsonDocument](
            (0 to 100).map(i => BsonDocument("name" -> s"doc-$i".asBson, "index" -> i.asBson))
          )
          docs <- coll.find
            .filter(Filter.lt("index", 10) || Filter.regex("name", "doc-[1-9]0"))
            .sortByDesc("name")
            .limit(5)
            .stream[BsonDocument]
            .compile
            .to(List)
          _ <- IO.println(docs)
        } yield ()
      }
    }
}
