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
import mongo4cats.embedded.EmbeddedMongo
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AsyncWordSpec

class MongoClientFSpec extends AsyncWordSpec with Matchers with EmbeddedMongo {

  override val mongoPort = 12345

  "A MongoDbClient" should {
    "connect to a db via connection string" in {
      withRunningEmbeddedMongo {
        MongoClientF
          .fromConnectionString[IO]("mongodb://localhost:12345")
          .use { client =>
            for {
              db    <- client.getDatabase("test-db")
              names <- db.collectionNames
            } yield names
          }
          .attempt
          .map(_ mustBe Right(Nil))
      }.unsafeToFuture()
    }

    "connect to a db via server address class" in {
      withRunningEmbeddedMongo {
        MongoClientF
          .fromServerAddress[IO](ServerAddress("localhost", 12345))
          .use { client =>
            for {
              db    <- client.getDatabase("test-db")
              names <- db.collectionNames
            } yield names
          }
          .attempt
          .map(_ mustBe Right(Nil))
      }.unsafeToFuture()
    }

    "return error when port is invalid" in {
      withRunningEmbeddedMongo {
        MongoClientF
          .fromServerAddress[IO](ServerAddress("localhost", 123))
          .use { client =>
            for {
              db    <- client.getDatabase("test-db")
              names <- db.collectionNames
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
}
