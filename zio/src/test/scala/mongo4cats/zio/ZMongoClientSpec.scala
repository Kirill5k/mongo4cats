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

import com.mongodb.MongoTimeoutException
import com.mongodb.connection.ClusterConnectionMode
import mongo4cats.models.client.{MongoConnection, MongoCredential, ServerAddress}
import mongo4cats.zio.embedded.EmbeddedMongo
import zio.Scope
import zio.test._
import zio.test.Assertion._
import zio.test.TestAspect.sequential

object ZMongoClientSpec extends ZIOSpecDefault with EmbeddedMongo {

  override val mongoPort: Int = 27002

  val username = "username"
  val password = "password"

  override def spec: Spec[TestEnvironment with Scope, Any] = suite("A ZMongoClient should")(
    test("connect to a db via connection string") {
      withRunningEmbeddedMongo {
        ZMongoClient
          .fromConnectionString(s"mongodb://localhost:$mongoPort")
          .map { client =>
            assert(client.clusterDescription.getConnectionMode)(equalTo(ClusterConnectionMode.SINGLE))
          }
      }
    },
    test("connect to a db via connection object") {
      withRunningEmbeddedMongo {
        ZMongoClient
          .fromConnection(MongoConnection.classic(mongoHost, mongoPort))
          .map { client =>
            assert(client.clusterDescription.getConnectionMode)(equalTo(ClusterConnectionMode.SINGLE))
          }
      }
    },
    test("connect to a db via connection object with authentication") {
      withRunningEmbeddedMongo(mongoHost, mongoPort, username, password) {
        ZMongoClient
          .fromConnection(MongoConnection.classic(mongoHost, mongoPort, Some(MongoCredential(username, password))))
          .map { client =>
            assert(client.clusterDescription.getConnectionMode)(equalTo(ClusterConnectionMode.SINGLE))
          }
      }
    },
    test("return current database names") {
      withRunningEmbeddedMongo {
        ZMongoClient
          .fromConnectionString(s"mongodb://localhost:$mongoPort")
          .flatMap { client =>
            for {
              db1   <- client.getDatabase("db1")
              _     <- db1.createCollection("coll")
              db2   <- client.getDatabase("db2")
              _     <- db2.createCollection("coll")
              names <- client.listDatabaseNames
            } yield assert(names)(hasSameElements(List("admin", "config", "db1", "db2", "local")))
          }
      }
    },
    test("return current databases") {
      withRunningEmbeddedMongo {
        ZMongoClient
          .fromConnectionString(s"mongodb://localhost:$mongoPort")
          .flatMap { client =>
            for {
              db1 <- client.getDatabase("db1")
              _   <- db1.createCollection("coll")
              dbs <- client.listDatabases
            } yield assert(dbs.flatMap(_.getString("name")))(hasSubset(List("local", "admin", "db1")))
          }
      }
    },
    test("connect to a db via server address class") {
      withRunningEmbeddedMongo {
        ZMongoClient
          .fromServerAddress(ServerAddress("localhost", mongoPort))
          .flatMap { client =>
            for {
              db    <- client.getDatabase("test-db")
              names <- db.listCollectionNames
            } yield assert(names)(isEmpty)
          }
      }
    },
    test("return error when port is invalid") {
      withRunningEmbeddedMongo {
        val result = ZMongoClient
          .fromServerAddress(ServerAddress("localhost", 123))
          .flatMap { client =>
            for {
              db    <- client.getDatabase("test-db")
              names <- db.listCollectionNames
            } yield names
          }

        assertZIO(result.exit)(fails(isSubtype[MongoTimeoutException](Assertion.anything)))
      }
    }
  ) @@ sequential
}
