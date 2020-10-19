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
import mongo4cats.EmbeddedMongo
import mongo4cats.client.MongoClientF
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.ExecutionContext

class MongoDatabaseFSpec extends AnyWordSpec with Matchers with EmbeddedMongo {

  implicit val cs = IO.contextShift(ExecutionContext.global)

  "A MongoDatabaseF" should {

    "create new collections and return collection names" in {
      withEmbeddedMongoClient { client =>
        val result = for {
          db    <- client.getDatabase("foo")
          _     <- db.createCollection("c1")
          _     <- db.createCollection("c2")
          names <- db.collectionNames()
        } yield names

        result.unsafeRunSync() must be(List("c2", "c1"))
      }
    }

    "return collection by name" in {
      withEmbeddedMongoClient { client =>
        val result = for {
          db         <- client.getDatabase("foo")
          _          <- db.createCollection("c1")
          collection <- db.getCollection("c1")
        } yield collection.namespace

        val namespace = result.unsafeRunSync()

        namespace.getDatabaseName must be("foo")
        namespace.getCollectionName must be("c1")
      }
    }
  }

  def withEmbeddedMongoClient[A](test: MongoClientF[IO] => A): A =
    withRunningEmbeddedMongo() {
      MongoClientF
        .fromConnectionString[IO]("mongodb://localhost:12345")
        .use { client =>
          IO(test(client))
        }
        .unsafeRunSync()
    }
}
