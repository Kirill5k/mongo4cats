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
import mongo4cats.bson.BsonDocument
import mongo4cats.bson.syntax._
import mongo4cats.client.MongoClient

object ManagingTransactions extends IOApp.Simple {

  override val run: IO[Unit] =
    MongoClient.fromConnectionString[IO]("mongodb://localhost:27017/?retryWrites=false").use {
      client =>
        for {
          db <- client.getDatabase("testdb")
          coll <- db.getCollection("docs")
          session <- client.startSession()
          _ <- session.startTransaction()
          _ <- (0 to 99).toList
            .traverse_(i =>
              coll.insertOne[BsonDocument](
                BsonDocument("name" -> s"doc-$i".asBson),
                clientSession = Some(session)
              )
            )
          _ <- session.abortTransaction
          count1 <- coll.count()
          _ <- IO.println(s"should be 0 since transaction was aborted: $count1")
          _ <- session.startTransaction()
          _ <- (0 to 99).toList
            .traverse_(i =>
              coll.insertOne[BsonDocument](
                BsonDocument("name" -> s"doc-$i".asBson),
                clientSession = Some(session)
              )
            )
          _ <- session.commitTransaction
          count2 <- coll.count()
          _ <- IO.println(s"should be 100 since transaction was committed: $count2")
        } yield ()
    }
}
