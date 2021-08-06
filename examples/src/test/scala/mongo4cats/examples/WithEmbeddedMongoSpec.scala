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
import mongo4cats.client.MongoClientF
import mongo4cats.embedded.EmbeddedMongo
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AsyncWordSpec

class WithEmbeddedMongoSpec extends AsyncWordSpec with Matchers with EmbeddedMongo {

  "A MongoCollectionF" should {
    "create and retrieve documents from a db" in withRunningEmbeddedMongo("localhost", 12344) {
      MongoClientF.fromConnectionString[IO]("mongodb://localhost:12344").use { client =>
        for {
          db   <- client.getDatabase("testdb")
          coll <- db.getCollection("docs")
          testDoc = Document("Hello", "World!")
          _        <- coll.insertOne[IO](testDoc)
          foundDoc <- coll.find.first[IO]
        } yield foundDoc mustBe testDoc
      }
    }.unsafeToFuture()
  }
}
