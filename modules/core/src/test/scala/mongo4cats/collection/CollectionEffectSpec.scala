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

package mongo4cats.collection

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import fs2.Stream
import mongo4cats.bson.Document
import mongo4cats.collection.CollectionEffectFixture._
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import java.util.concurrent.atomic.AtomicInteger

class CollectionEffectSpec extends AsyncWordSpec with Matchers {
  private val clientSession = session[IO]

  private val checkedOperations = operations[IO, Stream[IO, *]](clientSession) ++
    queryOperations[IO, Stream[IO, *]](clientSession, _.compile.drain)

  checkedOperations.foreach { operation =>
    s"Collection ${operation.label}" should {
      "defer invocation, create a new publisher on each execution and preserve results" in {
        val calls = new AtomicInteger()
        val coll  = new LiveMongoCollection[IO, Document](collection { (method, arguments) =>
          method mustBe operation.method
          operation.verifyArguments(arguments)
          if (operation.hasSession) {
            arguments(0) must be theSameInstanceAs clientSession.underlying
            ()
          }
          calls.incrementAndGet()
          operation.response()
        })
        val effect = operation.run(coll)
        calls.get() mustBe 0

        (for {
          first      <- effect
          afterFirst <- IO(calls.get())
          second     <- effect
        } yield {
          afterFirst mustBe 1
          calls.get() mustBe 2
          first mustBe operation.expected
          second mustBe operation.expected
        }).unsafeToFuture()
      }

      "capture synchronous driver exceptions without changing them" in {
        val error = new IllegalArgumentException("invalid driver argument")
        val calls = new AtomicInteger()
        val coll  = new LiveMongoCollection[IO, Document](collection { (_, _) =>
          calls.incrementAndGet()
          throw error
        })
        val effect = operation.run(coll)
        calls.get() mustBe 0

        effect.attempt
          .map { result =>
            result.swap.toOption.get must be theSameInstanceAs error
            calls.get() mustBe 1
          }
          .unsafeToFuture()
      }

      "preserve publisher failures" in {
        val error = new IllegalStateException("server rejected the operation")
        val coll  = new LiveMongoCollection[IO, Document](collection((_, _) => operation.response(Some(error))))
        operation.run(coll).attempt.map(_.swap.toOption.get must be theSameInstanceAs error).unsafeToFuture()
      }
    }
  }

  invalidArguments[IO, Stream[IO, *]](clientSession).foreach { case (label, run) =>
    s"Invalid $label conversion" should {
      "fail inside the effect without calling the driver" in {
        val calls = new AtomicInteger()
        val coll  = new LiveMongoCollection[IO, Document](collection { (_, _) =>
          calls.incrementAndGet()
          throw new AssertionError("Driver must not be called for invalid converted arguments")
        })
        val effect = run(coll)
        calls.get() mustBe 0

        effect.attempt
          .map { result =>
            result.swap.toOption.get mustBe a[RuntimeException]
            calls.get() mustBe 0
          }
          .unsafeToFuture()
      }
    }
  }
}
