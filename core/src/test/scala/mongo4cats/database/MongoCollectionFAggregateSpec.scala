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

package mongo4cats.database

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import mongo4cats.TestData
import mongo4cats.bson.Document
import mongo4cats.client.MongoClientF
import mongo4cats.database.operations.Aggregate
import mongo4cats.embedded.EmbeddedMongo
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import scala.concurrent.Future

class MongoCollectionFAggregateSpec extends AsyncWordSpec with Matchers with EmbeddedMongo {

  override val mongoPort = 12348

  "A MongoCollectionF" when {

    "aggregate" should {

      "join data from 2 collections" in {
        withEmbeddedMongoDatabase { db =>
          val result = for {
            accs <- db.getCollection("accounts")
            lookup = Aggregate.lookup("transactions", "_id", "account", "transactions")
            res <- accs.aggregate(lookup).first[IO]
          } yield res

          result.map { acc =>
            acc.getList("transactions", classOf[Document]) must have size 250
          }
        }
      }
    }
  }

  def withEmbeddedMongoDatabase[A](test: MongoDatabaseF[IO] => IO[A]): Future[A] =
    withRunningEmbeddedMongo {
      MongoClientF
        .fromConnectionString[IO](s"mongodb://localhost:$mongoPort")
        .use { client =>
          for {
            db  <- client.getDatabase("db")
            _   <- db.getCollection("accounts").flatMap(_.insertOne[IO](TestData.account))
            _   <- db.getCollection("categories").flatMap(_.insertMany[IO](TestData.categories))
            _   <- db.getCollection("transactions").flatMap(_.insertMany[IO](TestData.transactions(250)))
            res <- test(db)
          } yield res
        }
    }.unsafeToFuture()
}
