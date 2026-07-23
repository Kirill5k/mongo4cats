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
import mongo4cats.test.FreePort
import mongo4cats.zio.embedded.EmbeddedMongo
import zio.{Scope, ZIO}
import zio.test._
import zio.test.Assertion._
import zio.test.TestAspect.sequential

object ZMongoClientSpec extends ZIOSpecDefault with EmbeddedMongo {

  val username = "username"
  val password = "password"

  override def spec: Spec[TestEnvironment with Scope, Any] = suite("A ZMongoClient should")(
    test("connect to a db via connection string") {
      ZIO.succeed(FreePort.next()).flatMap { port =>
        withRunningEmbeddedMongo(port) {
          ZMongoClient
            .fromConnectionString(s"mongodb://localhost:$port")
            .map { client =>
              assert(client.clusterDescription.getConnectionMode)(equalTo(ClusterConnectionMode.SINGLE))
            }
        }
      }
    },
    test("connect to a db via connection object") {
      ZIO.succeed(FreePort.next()).flatMap { port =>
        withRunningEmbeddedMongo(port) {
          ZMongoClient
            .fromConnection(MongoConnection.classic("localhost", port))
            .map { client =>
              assert(client.clusterDescription.getConnectionMode)(equalTo(ClusterConnectionMode.SINGLE))
            }
        }
      }
    },
    test("connect to a db via connection object with authentication") {
      ZIO.succeed(FreePort.next()).flatMap { port =>
        withRunningEmbeddedMongo(port, username, password) {
          ZMongoClient
            .fromConnection(MongoConnection.classic("localhost", port, Some(MongoCredential(username, password))))
            .map { client =>
              assert(client.clusterDescription.getConnectionMode)(equalTo(ClusterConnectionMode.SINGLE))
            }
        }
      }
    },
    test("return current database names") {
      ZIO.succeed(FreePort.next()).flatMap { port =>
        withRunningEmbeddedMongo(port) {
          ZMongoClient
            .fromConnectionString(s"mongodb://localhost:$port")
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
      }
    },
    test("return current databases") {
      ZIO.succeed(FreePort.next()).flatMap { port =>
        withRunningEmbeddedMongo(port) {
          ZMongoClient
            .fromConnectionString(s"mongodb://localhost:$port")
            .flatMap { client =>
              for {
                db1 <- client.getDatabase("db1")
                _   <- db1.createCollection("coll")
                dbs <- client.listDatabases
              } yield assert(dbs.flatMap(_.getString("name")))(hasSubset(List("local", "admin", "db1")))
            }
        }
      }
    },
    test("connect to a db via server address class") {
      ZIO.succeed(FreePort.next()).flatMap { port =>
        withRunningEmbeddedMongo(port) {
          ZMongoClient
            .fromServerAddress(ServerAddress("localhost", port))
            .flatMap { client =>
              for {
                db    <- client.getDatabase("test-db")
                names <- db.listCollectionNames
              } yield assert(names)(isEmpty)
            }
        }
      }
    },
    test("return error when port is invalid") {
      ZIO.succeed(FreePort.next()).flatMap { port =>
        withRunningEmbeddedMongo(port) {
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
    }
  ) @@ sequential
}
