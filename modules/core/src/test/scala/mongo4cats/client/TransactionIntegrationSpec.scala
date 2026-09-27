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

package mongo4cats.client

import cats.effect.{Deferred, IO, Resource}
import cats.effect.unsafe.implicits.global
import mongo4cats.bson.Document
import mongo4cats.bson.syntax._
import mongo4cats.models.client.{ConnectionString, MongoClientSettings}
import mongo4cats.operations.Filter
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import java.util.UUID
import java.util.concurrent.TimeUnit
import scala.concurrent.duration._

/** Opt in with MONGO4CATS_TRANSACTION_URI pointing at a replica set or sharded deployment. */
class TransactionIntegrationSpec extends AsyncWordSpec with Matchers {
  "Managed transactions on MongoDB" should {
    "commit successful writes and roll back failures and cancellation" in {
      val uri =
        sys.env.getOrElse("MONGO4CATS_TRANSACTION_URI", cancel("Set MONGO4CATS_TRANSACTION_URI to run transaction integration tests"))
      val settings = MongoClientSettings
        .builder()
        .applyConnectionString(ConnectionString(uri))
        .timeout(10L, TimeUnit.SECONDS)
        .build()
      val collectionName = s"cats_${UUID.randomUUID().toString.replace("-", "")}"
      MongoClient
        .create[IO](settings)
        .use { client =>
          for {
            db     <- client.getDatabase("mongo4cats_transaction_tests")
            result <- Resource.make(db.getCollection(collectionName))(_.drop).use { coll =>
              val failure = new RuntimeException("rollback")
              for {
                // Materialize the collection before starting the transactions.
                _      <- coll.insertOne(Document("_id" := "setup"))
                value  <- client.transact(session => coll.insertOne(session, Document("_id" := "committed")).as(42))
                failed <- client.transact { session =>
                  coll.insertOne(session, Document("_id" := "failed")) *> IO.raiseError[Unit](failure)
                }.attempt
                written <- Deferred[IO, Unit]
                fiber   <- client.transact { session =>
                  coll.insertOne(session, Document("_id" := "canceled")) *> written.complete(()) *> IO.never[Unit]
                }.start
                _          <- written.get.timeout(20.seconds)
                _          <- fiber.cancel
                outcome    <- fiber.join
                committed  <- coll.count(Filter.eq("_id", "committed"))
                rolledBack <- coll.count(Filter.in("_id", List("failed", "canceled")))
              } yield {
                value mustBe 42
                failed mustBe Left(failure)
                outcome.isCanceled mustBe true
                committed mustBe 1L
                rolledBack mustBe 0L
              }
            }
          } yield result
        }
        .timeout(60.seconds)
        .unsafeToFuture()
    }
  }
}
