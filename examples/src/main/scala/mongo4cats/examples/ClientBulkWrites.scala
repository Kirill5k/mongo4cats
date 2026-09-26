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
import mongo4cats.models.client.{ClientBulkWriteOptions, ClientWriteCommand}
import mongo4cats.models.collection.MongoNamespace
import mongo4cats.operations.{Filter, Update}

/** Requires MongoDB 8.0 or later. */
object ClientBulkWrites extends IOApp.Simple {

  private val products = MongoNamespace("shop", "products")
  private val events   = MongoNamespace("audit", "events")

  override val run: IO[Unit] =
    MongoClient.fromConnectionString[IO]("mongodb://localhost:27017").use { client =>
      val commands: List[ClientWriteCommand] = List(
        ClientWriteCommand.InsertOne(products, Document("sku" := "widget", "qty" := 10)),
        ClientWriteCommand.UpdateOne(products, Filter.eq("sku", "widget"), Update.inc("qty", 5)),
        ClientWriteCommand.InsertOne(events, Document("action" := "stock-added", "sku" := "widget"))
      )

      for {
        result <- client.bulkWrite(commands, ClientBulkWriteOptions(verboseResults = true))
        _      <- IO.println(s"Inserted: ${result.getInsertedCount}, modified: ${result.getModifiedCount}")
        _      <- IO.println(s"Individual results: ${result.getVerboseResults}")
      } yield ()
    }
}
