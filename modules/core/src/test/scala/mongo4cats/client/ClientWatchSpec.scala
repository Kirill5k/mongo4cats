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

import cats.effect.{IO, Resource}
import cats.effect.unsafe.implicits.global
import fs2.Stream
import mongo4cats.collection.CollectionEffectFixture.session
import mongo4cats.database.CodecInheritanceFixture
import mongo4cats.queries.WatchFixture._
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AsyncWordSpec

class ClientWatchSpec extends AsyncWordSpec with Matchers {
  private val clientSession = session[IO]

  clientOperations[IO, Stream[IO, *], Resource[IO, *]](clientSession).foreach { operation =>
    s"Client watch ${operation.label}" should {
      "defer publisher creation, preserve codecs and arguments, and emit events on every execution" in {
        val driver = new Driver(initialRegistry = clientRegistry)
        val source = new LiveMongoClient[IO](driver.client)
        val query  = operation.query(source)
        val effect = query.stream.compile.toList

        driver.calls.get() mustBe 0
        driver.registryCalls.get() mustBe 0

        (for {
          first  <- effect
          second <- effect
          third  <- query.boundedStream(2).compile.toList
        } yield {
          List(first, second, third) mustBe List.fill(3)(List(expectedEvent))
          driver.calls.get() mustBe 3
          driver.invocations.map(_.publisherId).distinct.size mustBe 3
          driver.invocations.foreach { invocation =>
            operation.verify(invocation, "client", clientSession)
            invocation.registry.get(classOf[CodecInheritanceFixture.Marker]) must be theSameInstanceAs markerCodec
            invocation.registry.get(classOf[String]) must be theSameInstanceAs stringCodec
          }
          source.underlying.getCodecRegistry must be theSameInstanceAs clientRegistry
        }).unsafeToFuture()
      }

      "capture synchronous driver failures when the stream runs" in {
        val error  = new IllegalArgumentException("watch creation failed")
        val driver = new Driver(synchronousFailure = Some(error))
        val source = new LiveMongoClient[IO](driver.client)
        val effect = operation.query(source).stream.compile.toList

        driver.calls.get() mustBe 0
        effect.attempt
          .map { result =>
            result.swap.toOption.get must be theSameInstanceAs error
            driver.calls.get() mustBe 1
          }
          .unsafeToFuture()
      }

      "preserve asynchronous publisher failures" in {
        val error  = new IllegalStateException("watch subscription failed")
        val driver = new Driver(publisherFailure = Some(error))
        val source = new LiveMongoClient[IO](driver.client)
        operation
          .query(source)
          .boundedStream(2)
          .compile
          .toList
          .attempt
          .map(_.swap.toOption.get must be theSameInstanceAs error)
          .unsafeToFuture()
      }
    }
  }

  "Client watch codecs" should {
    "honor an explicitly configured Document codec for both images" in {
      val custom                  = new DocumentOverride
      val driver                  = new Driver(initialRegistry = custom.registry)
      val source: MongoClient[IO] = new LiveMongoClient[IO](driver.client)
      val effect                  = source.watch.stream.compile.toList

      custom.decoded.get() mustBe 0
      effect
        .map { events =>
          events mustBe List(custom.expected)
          custom.decoded.get() mustBe 2
          driver.invocations.head.registry.get(classOf[mongo4cats.bson.Document]) must be theSameInstanceAs custom.codec
          source.underlying.getCodecRegistry must be theSameInstanceAs custom.registry
        }
        .unsafeToFuture()
    }
  }

}
