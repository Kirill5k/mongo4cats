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

import mongo4cats.database.CodecInheritanceFixture._
import zio.{Scope, ZIO}
import zio.test._

import scala.concurrent.duration._

object TimeoutSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("Database and collection timeouts")(
    test("inherit driver settings and retain independent overrides through codecs and document types") {
      val marker = codec[Marker]
      ZIO.scoped[Any] {
        for {
          client   <- ZMongoClient.fromConnectionString("mongodb://localhost:27017")
          original <- client.getDatabase("timeouts")
          configured = original.withTimeout(5.seconds).withAddedCodec(registry(marker))
          collection <- configured.getCollection("documents")
          sibling    <- original.getCollection("documents")
          unlimited  <- configured.withTimeout(Duration.Zero).getCollection("documents")
          overridden = collection.withTimeout(1500.millis).withAddedCodec(registry(marker)).as[Marker]
          databaseError   <- ZIO.attempt(configured.withTimeout((-1).millis)).either
          collectionError <- ZIO.attempt(collection.withTimeout((-1).millis)).either
          negativeNanos   <- ZIO.attempt(configured.withTimeout((-1).nanos)).either
          subMillisecond  <- ZIO.attempt(collection.withTimeout(1.nano)).either
        } yield assertTrue(
          original.timeout.isEmpty,
          sibling.timeout.isEmpty,
          configured.timeout.contains(5.seconds),
          collection.timeout.contains(5.seconds),
          unlimited.timeout.contains(Duration.Zero),
          overridden.timeout.contains(1500.millis),
          overridden.codecs.get(classOf[Marker]) eq marker,
          overridden.documentClass == classOf[Marker],
          collection.withTimeout(Duration.Zero).timeout.contains(Duration.Zero),
          databaseError.left.exists(_.isInstanceOf[IllegalArgumentException]),
          collectionError.left.exists(_.isInstanceOf[IllegalArgumentException]),
          negativeNanos.left.exists(_.isInstanceOf[IllegalArgumentException]),
          subMillisecond.left.exists(_.isInstanceOf[IllegalArgumentException])
        )
      }
    },
    test("inherit a timeout from client configuration") {
      ZIO.scoped[Any] {
        for {
          client     <- ZMongoClient.fromConnectionString("mongodb://localhost:27017/?timeoutMS=2300")
          database   <- client.getDatabase("timeouts")
          collection <- database.getCollection("documents")
        } yield assertTrue(database.timeout.contains(2300.millis), collection.timeout.contains(2300.millis))
      }
    }
  )
}
