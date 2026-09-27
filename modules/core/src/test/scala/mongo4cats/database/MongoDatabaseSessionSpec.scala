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
import mongo4cats.client.SessionParityFixture
import mongo4cats.collection.{CollectionEffectFixture, SearchIndexFixture}
import mongo4cats.models.database.CreateCollectionOptions
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}

class MongoDatabaseSessionSpec extends AsyncWordSpec with Matchers {
  private val session = CollectionEffectFixture.session[IO]
  private val options = CreateCollectionOptions(capped = true, sizeInBytes = 2048L, maxDocuments = 100L)
  private val operations: List[(String, Option[CreateCollectionOptions], MongoDatabase[IO] => IO[Unit])] = List(
    ("default options", None, _.createCollection(session, "events")),
    ("explicit options", Some(options), _.createCollection(session, "events", options))
  )

  operations.foreach { case (name, suppliedOptions, create) =>
    s"Creating a collection with a session and $name" should {
      "defer and repeat driver invocation while forwarding name, session and options" in {
        val calls    = new AtomicInteger()
        val observed = new AtomicReference[Array[AnyRef]]()
        val database = new LiveMongoDatabase[IO](SessionParityFixture.database { args =>
          calls.incrementAndGet()
          observed.set(args)
          SearchIndexFixture.publisher[Void](Nil)
        })
        val effect = create(database)
        calls.get() mustBe 0
        (for {
          _          <- effect
          afterFirst <- IO(calls.get())
          _          <- effect
        } yield {
          val args = observed.get()
          afterFirst mustBe 1
          calls.get() mustBe 2
          args.length mustBe 3
          args(0) must be theSameInstanceAs session.underlying
          args(1) mustBe "events"
          args(2).toString mustBe suppliedOptions.getOrElse(CreateCollectionOptions()).toString
          suppliedOptions match {
            case Some(expected) => args(2) must be theSameInstanceAs expected
            case None           => succeed
          }
        }).unsafeToFuture()
      }

      List(true, false).foreach { synchronous =>
        s"capture ${if (synchronous) "synchronous driver" else "publisher"} failures" in {
          val calls    = new AtomicInteger()
          val error    = new IllegalStateException("create collection failed")
          val database = new LiveMongoDatabase[IO](SessionParityFixture.database { _ =>
            calls.incrementAndGet()
            if (synchronous) throw error
            else SearchIndexFixture.publisher[Void](Nil, Some(error))
          })
          val effect = create(database)
          calls.get() mustBe 0
          effect.attempt
            .map { result =>
              result.swap.toOption.get must be theSameInstanceAs error
              calls.get() mustBe 1
            }
            .unsafeToFuture()
        }
      }
    }
  }
}
