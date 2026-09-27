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
import mongo4cats.bson.syntax._
import mongo4cats.client.MongoClient

object Transactions extends IOApp.Simple {

  override val run: IO[Unit] =
    MongoClient.fromConnectionString[IO]("mongodb://localhost:27017/?retryWrites=false").use { client =>
      for {
        db       <- client.getDatabase("my-db")
        coll     <- db.getCollection("docs")
        baseline <- coll.count
        _        <- client.startSession.use { session =>
          for {
            // The existing manual API remains available.
            _      <- session.startTransaction
            _      <- (0 to 99).toList.traverse_(i => coll.insertOne(session, Document("name" := s"doc-$i")))
            _      <- session.abortTransaction
            count1 <- coll.count
            _      <- IO.println(s"Manual abort added ${count1 - baseline} documents (expected 0)")
            _      <- session.startTransaction
            _      <- (0 to 99).toList.traverse_(i => coll.insertOne(session, Document("name" := s"doc-$i")))
            _      <- session.commitTransaction
            count2 <- coll.count
            _      <- IO.println(s"Manual commit added ${count2 - count1} documents (expected 100)")

            // withTransaction manages rollback but leaves this session open.
            expectedFailure = new RuntimeException("Demonstrating managed rollback")
            _ <- session
              .withTransaction {
                for {
                  _ <- coll.insertOne(session, Document("name" := "rolled-back"))
                  _ <- IO.raiseError[Unit](expectedFailure)
                } yield ()
              }
              .handleErrorWith {
                case error if (error eq expectedFailure) && error.getSuppressed.isEmpty =>
                  IO.println(s"Preserved original error: ${error.getMessage}")
                case error => IO.raiseError[Unit](error)
              }
            count3 <- coll.count
            _      <- IO.println(s"Managed rollback added ${count3 - count2} documents (expected 0)")
          } yield ()
        }
        // transact acquires and closes its own session. Its callback may run more than once.
        inserted <- client.transact { session =>
          for {
            _ <- (100 to 199).toList.traverse_(i => coll.insertOne(session, Document("name" := s"doc-$i")))
          } yield 100
        }
        count <- coll.count
        _     <- IO.println(s"Managed commit returned $inserted; total added ${count - baseline} documents (expected 200)")
      } yield ()
    }
}
