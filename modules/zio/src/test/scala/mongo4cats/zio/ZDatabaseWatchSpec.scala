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

import mongo4cats.collection.CollectionEffectFixture.session
import mongo4cats.database.CodecInheritanceFixture
import mongo4cats.queries.WatchFixture._
import zio.{durationInt, Scope, Task}
import zio.stream.Stream
import zio.test._

object ZDatabaseWatchSpec extends ZIOSpecDefault {
  private val clientSession = session[Task]

  override def spec: Spec[TestEnvironment with Scope, Any] = suite("Database watches")(
    databaseOperations[Task, Stream[Throwable, *]](clientSession).map { operation =>
      suite(operation.label)(
        test("defer publisher creation, preserve codecs and arguments, and emit events on every execution") {
          val driver = new Driver(initialRegistry = databaseRegistry)
          val source = new ZMongoDatabaseLive(driver.database)
          val query  = operation.query(source)
          val effect = query.stream.runCollect
          val before = driver.calls.get()
          val registryBefore = driver.registryCalls.get()

          for {
            first  <- effect
            second <- effect
            third  <- query.boundedStream(2).runCollect
          } yield {
            driver.invocations.foreach(operation.verify(_, "database", clientSession))
            assertTrue(
              before == 0,
              registryBefore == 0,
              List(first.toList, second.toList, third.toList) == List.fill(3)(List(expectedEvent)),
              driver.calls.get() == 3,
              driver.invocations.map(_.publisherId).distinct.size == 3,
              driver.invocations.forall(_.registry.get(classOf[CodecInheritanceFixture.Marker]) eq markerCodec),
              driver.invocations.forall(_.registry.get(classOf[String]) eq stringCodec),
              source.underlying.getCodecRegistry eq databaseRegistry
            )
          }
        },
        test("capture synchronous driver failures in the stream error channel") {
          val error  = new IllegalArgumentException("watch creation failed")
          val driver = new Driver(synchronousFailure = Some(error))
          val source = new ZMongoDatabaseLive(driver.database)
          val effect = operation.query(source).stream.runCollect
          val before = driver.calls.get()

          effect.either.map(result => assertTrue(before == 0, result.left.exists(_ eq error), driver.calls.get() == 1))
        },
        test("preserve asynchronous publisher failures") {
          val error  = new IllegalStateException("watch subscription failed")
          val driver = new Driver(publisherFailure = Some(error))
          val source = new ZMongoDatabaseLive(driver.database)

          operation.query(source).boundedStream(2).runCollect.either
            .map(result => assertTrue(result.left.exists(_ eq error)))
        }
      )
    } :+ test("honor an explicitly configured Document codec for both images") {
      val custom = new DocumentOverride
      val driver = new Driver(initialRegistry = custom.registry)
      val source: ZMongoDatabase = new ZMongoDatabaseLive(driver.database)
      val effect = source.watch.stream.runCollect
      val before = custom.decoded.get()

      effect.map { events =>
        assertTrue(
          before == 0,
          events.toList == List(custom.expected),
          custom.decoded.get() == 2,
          driver.invocations.head.registry.get(classOf[mongo4cats.bson.Document]) eq custom.codec,
          source.underlying.getCodecRegistry eq custom.registry
        )
      }
    }
  ) @@ TestAspect.timeout(10.seconds)
}
