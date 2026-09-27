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

import mongo4cats.bson.Document
import mongo4cats.collection.CollectionEffectFixture._
import zio.{durationInt, Scope, Task, ZIO}
import zio.stream.Stream
import zio.test._

import java.util.concurrent.atomic.AtomicInteger

object ZCollectionEffectSpec extends ZIOSpecDefault {
  private val clientSession     = session[Task]
  private val checkedOperations = operations[Task, Stream[Throwable, *]](clientSession) ++
    queryOperations[Task, Stream[Throwable, *]](clientSession, _.runDrain)

  override def spec: Spec[TestEnvironment with Scope, Any] = suite("Collection effects")(
    suite("operation contracts")(
      checkedOperations.map { operation =>
        suite(operation.label)(
          test("defer invocation, create a new publisher on each execution and preserve results") {
            val calls = new AtomicInteger()
            val coll  = new ZMongoCollectionLive[Document](collection { (method, arguments) =>
              require(method == operation.method, s"Unexpected driver method: $method")
              operation.verifyArguments(arguments)
              require(!operation.hasSession || (arguments(0) eq clientSession.underlying), "Expected session was not forwarded")
              calls.incrementAndGet()
              operation.response()
            })
            val effect = operation.run(coll)
            val before = calls.get()

            for {
              first      <- effect
              afterFirst <- ZIO.succeed(calls.get())
              second     <- effect
            } yield assertTrue(before == 0, afterFirst == 1, calls.get() == 2, first == operation.expected, second == operation.expected)
          },
          test("capture synchronous driver exceptions in the error channel without changing them") {
            val error = new IllegalArgumentException("invalid driver argument")
            val calls = new AtomicInteger()
            val coll  = new ZMongoCollectionLive[Document](collection { (_, _) =>
              calls.incrementAndGet()
              throw error
            })
            val effect = operation.run(coll)
            val before = calls.get()

            effect.either.map(result => assertTrue(before == 0, result.left.exists(_ eq error), calls.get() == 1))
          },
          test("preserve publisher failures") {
            val error = new IllegalStateException("server rejected the operation")
            val coll  = new ZMongoCollectionLive[Document](collection((_, _) => operation.response(Some(error))))
            operation.run(coll).either.map(result => assertTrue(result.left.exists(_ eq error)))
          }
        )
      }
    ),
    suite("argument conversion")(
      invalidArguments[Task, Stream[Throwable, *]](clientSession).map { case (label, run) =>
        test(s"invalid $label fails inside the effect without calling the driver") {
          val calls = new AtomicInteger()
          val coll  = new ZMongoCollectionLive[Document](collection { (_, _) =>
            calls.incrementAndGet()
            throw new AssertionError("Driver must not be called for invalid converted arguments")
          })
          val effect = run(coll)
          val before = calls.get()

          effect.either.map(result => assertTrue(before == 0, result.left.exists(_.isInstanceOf[RuntimeException]), calls.get() == 0))
        }
      }
    )
  ) @@ TestAspect.timeout(10.seconds)
}
