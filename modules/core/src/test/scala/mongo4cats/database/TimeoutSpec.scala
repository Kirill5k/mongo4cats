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
import mongo4cats.client.MongoClient
import mongo4cats.database.CodecInheritanceFixture._
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import scala.concurrent.duration._

class TimeoutSpec extends AsyncWordSpec with Matchers {
  "Database and collection timeouts" should {
    "inherit driver settings and retain independent immutable overrides through codecs and document types" in {
      val marker = codec[Marker]
      MongoClient
        .fromConnectionString[IO]("mongodb://localhost:27017")
        .use { client =>
          for {
            original <- client.getDatabase("timeouts")
            configured = original.withTimeout(5.seconds).withAddedCodec(registry(marker))
            collection <- configured.getCollection("documents")
            sibling    <- original.getCollection("documents")
            unlimited  <- configured.withTimeout(Duration.Zero).getCollection("documents")
          } yield {
            original.timeout mustBe None
            sibling.timeout mustBe None
            configured.timeout mustBe Some(5.seconds)
            collection.timeout mustBe Some(5.seconds)
            unlimited.timeout mustBe Some(Duration.Zero)
            val overridden = collection.withTimeout(1500.millis).withAddedCodec(registry(marker)).as[Marker]
            overridden.timeout mustBe Some(1500.millis)
            overridden.codecs.get(classOf[Marker]) must be theSameInstanceAs marker
            overridden.documentClass mustBe classOf[Marker]
            collection.timeout mustBe Some(5.seconds)
            collection.withTimeout(Duration.Zero).timeout mustBe Some(Duration.Zero)
            an[IllegalArgumentException] must be thrownBy configured.withTimeout((-1).millis)
            an[IllegalArgumentException] must be thrownBy collection.withTimeout((-1).millis)
            an[IllegalArgumentException] must be thrownBy configured.withTimeout((-1).nanos)
            an[IllegalArgumentException] must be thrownBy collection.withTimeout(1.nano)
          }
        }
        .unsafeToFuture()
    }

    "inherit a timeout from client configuration" in
      MongoClient
        .fromConnectionString[IO]("mongodb://localhost:27017/?timeoutMS=2300")
        .use { client =>
          for {
            database   <- client.getDatabase("timeouts")
            collection <- database.getCollection("documents")
          } yield {
            database.timeout mustBe Some(2300.millis)
            collection.timeout mustBe Some(2300.millis)
          }
        }
        .unsafeToFuture()
  }
}
