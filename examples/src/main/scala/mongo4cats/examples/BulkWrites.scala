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
import mongo4cats.bson.Document
import mongo4cats.bson.syntax._
import mongo4cats.client.MongoClient
import mongo4cats.collection.{BulkWriteOptions, WriteCommand}
import mongo4cats.operations.{Filter, Update}

object BulkWrites extends IOApp.Simple {

  override val run: IO[Unit] =
    MongoClient.fromConnectionString[IO]("mongodb://localhost:27017").use { client =>
      for {
        db   <- client.getDatabase("testdb")
        coll <- db.getCollection("docs")
        _    <- coll.insertMany((0 to 100).map(i => Document("name" := s"doc-$i", "index" := i)))
        commands = List(
          WriteCommand.DeleteOne(Filter.eq("name", "doc-1")),
          WriteCommand.InsertOne(Document("name" := "doc-101", "index" := 101)),
          WriteCommand.DeleteMany(Filter.gt("index", 10) && Filter.lt("index", 20)),
          WriteCommand.UpdateOne(Filter.eq("index", 50), Update.set("name", "doc-50-updated"))
        )
        res <- coll.bulkWrite(commands, BulkWriteOptions(ordered = false))
        _   <- IO.println(res)
      } yield ()
    }
}
