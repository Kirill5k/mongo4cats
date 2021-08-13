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
import mongo4cats.database.operations.{Accumulator, Aggregate, Filter, Projection, Sort}
import mongo4cats.embedded.EmbeddedMongo
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import scala.concurrent.Future

class MongoCollectionFAggregateSpec extends AsyncWordSpec with Matchers with EmbeddedMongo {

  override val mongoPort = 12349

  "A MongoCollectionF" when {

    "aggregate" should {

      "join data from 2 collections" in {
        withEmbeddedMongoDatabase { db =>
          val result = for {
            accs <- db.getCollection("accounts")
            res <- accs
              .aggregate[Document] {
                Aggregate
                  .matchBy(Filter.eq("currency", TestData.USD))
                  .lookup("transactions", "_id", "account", "transactions")
                  .project(
                    Projection
                      .include(List("transactions", "name", "currency"))
                      .computed("totalAmount", Document("$sum" -> "$transactions.amount"))
                  )
              }
              .first[IO]
          } yield res

          result.map { acc =>
            acc.get.getList("transactions", classOf[Document]) must have size 250
            acc.get.getInteger("totalAmount").intValue() must be > 0
          }
        }
      }

      "group by field" in {
        withEmbeddedMongoDatabase { db =>
          val result = for {
            transactions <- db.getCollection("transactions")
            accumulator = Accumulator
              .sum("count", 1)
              .sum("totalAmount", "$amount")
              .first("categoryId", "$category._id")
            res <- transactions
              .aggregate[Document] {
                Aggregate
                  .group("$category", accumulator)
                  .lookup("categories", "categoryId", "_id", "category")
                  .sort(Sort.desc("count"))
              }
              .all[IO]
          } yield res

          result.map { cats =>
            cats must have size 10
            val counts = cats.map(_.getInteger("count").intValue()).toList
            counts.reverse mustBe sorted
            counts.sum mustBe 250
          }
        }
      }

      "explain the pipeline in a form of a document" in {
        withEmbeddedMongoDatabase { db =>
          val result = for {
            accs <- db.getCollection("accounts")
            res <- accs
              .aggregate[Document] {
                Aggregate
                  .matchBy(Filter.eq("currency", TestData.USD))
                  .lookup("transactions", "_id", "account", "transactions")
                  .sort(Sort.asc("name"))
                  .project(Projection.excludeId)
              }
              .explain[IO]
          } yield res

          result.map { expl =>
            println(expl.toJson)
            expl.getDouble("ok").doubleValue() mustBe 1.0
            expl.getList("stages", classOf[Document]).size() mustBe 4
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
            _   <- db.getCollection("accounts").flatMap(_.insertMany[IO](TestData.accounts))
            _   <- db.getCollection("categories").flatMap(_.insertMany[IO](TestData.categories))
            _   <- db.getCollection("transactions").flatMap(_.insertMany[IO](TestData.transactions(250)))
            res <- test(db)
          } yield res
        }
    }.unsafeToFuture()
}
