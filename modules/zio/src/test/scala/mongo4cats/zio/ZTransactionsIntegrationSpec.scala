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

package mongo4cats.zio

import mongo4cats.bson.Document
import mongo4cats.bson.syntax._
import mongo4cats.models.client.{ConnectionString, MongoClientSettings, TransactionOptions, TransactionRetryPolicy}
import zio.{durationInt, Promise, RIO, Scope, Task, ZIO}
import zio.test._

import java.util.UUID
import java.util.concurrent.TimeUnit

/** Set MONGO4CATS_TRANSACTION_URI to a replica-set or sharded-cluster URI to run these tests. */
object ZTransactionsIntegrationSpec extends ZIOSpecDefault {
  private val uri     = sys.env.get("MONGO4CATS_TRANSACTION_URI").filter(_.nonEmpty)
  private val options = TransactionOptions.builder.maxCommitTime(10L, TimeUnit.SECONDS).build()

  override def spec: Spec[TestEnvironment with Scope, Any] = {
    val tests = suite("Managed ZIO transactions against MongoDB")(
      test("commit session-bound writes") {
        withCollection { (client, collection) =>
          for {
            value <- client.transact(options = options, retryPolicy = TransactionRetryPolicy.none) { session =>
              collection.insertOne(session, Document("_id" := 1)).as(42)
            }
            count <- collection.count
          } yield assertTrue(value == 42, count == 1L)
        }
      },
      test("roll back failed bodies and preserve the original failure") {
        withCollection { (client, collection) =>
          val error = new IllegalStateException("rollback integration test")
          for {
            result <- client
              .transact(options = options, retryPolicy = TransactionRetryPolicy.none) { session =>
                collection.insertOne(session, Document("_id" := 1)) *> ZIO.fail(error)
              }
              .either
            count <- collection.count
          } yield assertTrue(result.left.exists(_ eq error), count == 0L)
        }
      },
      test("roll back interrupted bodies before releasing the session") {
        withCollection { (client, collection) =>
          for {
            inserted <- Promise.make[Nothing, Unit]
            fiber    <- client
              .transact(options = options, retryPolicy = TransactionRetryPolicy.none) { session =>
                collection.insertOne(session, Document("_id" := 1)) *> inserted.succeed(()) *> ZIO.never
              }
              .fork
            _     <- inserted.await
            exit  <- fiber.interrupt
            count <- collection.count
          } yield assertTrue(exit.isInterrupted, count == 0L)
        }
      }
    ) @@ TestAspect.withLiveClock @@ TestAspect.timeout(1.minute)
    if (uri.isDefined) tests else tests @@ TestAspect.ignore
  }

  private def withCollection[A](run: (ZMongoClient, ZMongoCollection[Document]) => Task[A]): RIO[Scope, A] =
    for {
      client <- ZMongoClient.create(
        MongoClientSettings
          .builder()
          .applyConnectionString(ConnectionString(uri.get))
          .timeout(10L, TimeUnit.SECONDS)
          .build()
      )
      database   <- client.getDatabase("mongo4cats_transaction_tests")
      name       <- ZIO.succeed(s"zio_${UUID.randomUUID().toString.replace('-', '_')}")
      collection <- ZIO.acquireRelease(database.createCollection(name) *> database.getCollection(name))(_.drop.orDie)
      result     <- run(client, collection)
    } yield result
}
