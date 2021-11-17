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

import cats.effect.IO
import cats.syntax.either._
import cats.effect.unsafe.implicits.global
import com.mongodb.MongoTimeoutException
import com.mongodb.connection.ClusterConnectionMode
import mongo4cats.embedded.EmbeddedMongo
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AsyncWordSpec

class MongoClientSpec extends AsyncWordSpec with Matchers with EmbeddedMongo {

  override val mongoPort = 12345

  "A MongoClient" should {
    "connect to a db via connection string" in withRunningEmbeddedMongo {
      MongoClient
        .fromConnectionString[IO]("mongodb://localhost:12345")
        .use { client =>
          val cluster = client.clusterDescription

          IO.pure(cluster.getConnectionMode mustBe ClusterConnectionMode.SINGLE)
        }
    }.unsafeToFuture()

    "return current database names" in withRunningEmbeddedMongo {
      MongoClient
        .fromConnectionString[IO]("mongodb://localhost:12345")
        .use { client =>
          for {
            db1   <- client.getDatabase("db1")
            _     <- db1.createCollection("coll")
            db2   <- client.getDatabase("db2")
            _     <- db2.createCollection("coll")
            names <- client.listDatabaseNames
          } yield names
        }
        .map(_ mustBe List("admin", "config", "db1", "db2", "local"))
    }.unsafeToFuture()

    "return current databases" in withRunningEmbeddedMongo {
      MongoClient
        .fromConnectionString[IO]("mongodb://localhost:12345")
        .use { client =>
          for {
            db1 <- client.getDatabase("db1")
            _   <- db1.createCollection("coll")
            dbs <- client.listDatabases
          } yield dbs
        }
        .map { dbs =>
          dbs must have size 3
          val adminDb = dbs.head
          adminDb.getString("name") mustBe "admin"
          adminDb.getBoolean("empty") mustBe false
        }
    }.unsafeToFuture()

    "connect to a db via server address class" in withRunningEmbeddedMongo {
      MongoClient
        .fromServerAddress[IO](ServerAddress("localhost", 12345))
        .use { client =>
          for {
            db    <- client.getDatabase("test-db")
            names <- db.listCollectionNames
          } yield names
        }
        .map(_ mustBe Nil)
    }.unsafeToFuture()

    "return error when port is invalid" in withRunningEmbeddedMongo {
      MongoClient
        .fromServerAddress[IO](ServerAddress("localhost", 123))
        .use { client =>
          for {
            db    <- client.getDatabase("test-db")
            names <- db.listCollectionNames
          } yield names
        }
        .attempt
        .map { res =>
          res.isLeft mustBe true
          res.leftMap(_.asInstanceOf[MongoTimeoutException].getCode) mustBe (Left(-3))
        }
    }.unsafeToFuture()
  }
}
