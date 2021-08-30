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
import cats.syntax.foldable._
import mongo4cats.bson.Document
import mongo4cats.client.MongoClient
import mongo4cats.embedded.EmbeddedMongo

object ManagingTransactions extends IOApp.Simple with EmbeddedMongo {

  override val run: IO[Unit] =
    withRunningEmbeddedMongo(host = "localhost", port = 27017) {
      MongoClient.fromConnectionString[IO]("mongodb://localhost:27017").use { client =>
        for {
          session <- client.startSession
          db      <- client.getDatabase("testdb")
          coll    <- db.getCollection("docs")
          _       <- session.startTransaction
          _       <- (0 to 99).toList.traverse_(i => coll.insertOne(Document("name" -> s"doc-$i")))
          _       <- session.abortTransaction
          count1  <- coll.count
          _       <- IO.println(s"should be 0: $count1")
          _       <- session.startTransaction
          _       <- (0 to 99).toList.traverse_(i => coll.insertOne(Document("name" -> s"doc-$i")))
          _       <- session.commitTransaction
          count2  <- coll.count
          _       <- IO.println(s"should be 100: $count2")
        } yield ()
      }
    }
}
