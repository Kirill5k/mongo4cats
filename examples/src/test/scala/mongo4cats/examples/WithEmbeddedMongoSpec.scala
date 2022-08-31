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

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import mongo4cats.bson.Document
import mongo4cats.bson.syntax._
import mongo4cats.client.MongoClient
import mongo4cats.embedded.EmbeddedMongo
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AsyncWordSpec

class WithEmbeddedMongoSpec extends AsyncWordSpec with Matchers with EmbeddedMongo {

  override val mongoPort: Int = 12344

  "A MongoCollection" should {
    "create and retrieve documents from a db" in withRunningEmbeddedMongo {
      MongoClient.fromConnectionString[IO]("mongodb://localhost:12344").use { client =>
        for {
          db   <- client.getDatabase("my-db")
          coll <- db.getCollection("docs")
          testDoc = Document("Hello" := "World!")
          _        <- coll.insertOne(testDoc)
          foundDoc <- coll.find.first
        } yield foundDoc.map(_.remove("_id")) mustBe Some(testDoc)
      }
    }.unsafeToFuture()

    "start instance on different port" in withRunningEmbeddedMongo("localhost", 12355) {
      MongoClient.fromConnectionString[IO]("mongodb://localhost:12355").use { client =>
        for {
          db   <- client.getDatabase("my-db")
          coll <- db.getCollection("docs")
          testDoc = Document("Hello" := "World!")
          _        <- coll.insertOne(testDoc)
          foundDoc <- coll.find.first
        } yield foundDoc.map(_.remove("_id")) mustBe Some(testDoc)
      }
    }.unsafeToFuture()
  }
}
