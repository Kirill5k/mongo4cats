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
import cats.effect.unsafe.implicits.global
import mongo4cats.collection.{CollectionEffectFixture, SearchIndexFixture}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}

class ClientSessionParitySpec extends AsyncWordSpec with Matchers {
  private val session = CollectionEffectFixture.session[IO]

  "Listing database names with a session" should {
    "defer and repeat driver invocation while forwarding the session" in {
      val calls    = new AtomicInteger()
      val observed = new AtomicReference[Array[AnyRef]]()
      val client   = new LiveMongoClient[IO](SessionParityFixture.client { args =>
        calls.incrementAndGet()
        observed.set(args)
        SearchIndexFixture.publisher(SessionParityFixture.names)
      })
      val effect = client.listDatabaseNames(session)
      calls.get() mustBe 0
      (for {
        first      <- effect
        afterFirst <- IO(calls.get())
        second     <- effect
      } yield {
        first.toList mustBe SessionParityFixture.names
        second.toList mustBe SessionParityFixture.names
        afterFirst mustBe 1
        calls.get() mustBe 2
        observed.get().length mustBe 1
        observed.get()(0) must be theSameInstanceAs session.underlying
      }).unsafeToFuture()
    }

    "return an empty iterable for an empty publisher" in {
      val client = new LiveMongoClient[IO](SessionParityFixture.client(_ => SearchIndexFixture.publisher[String](Nil)))
      client.listDatabaseNames(session).map(_ mustBe empty).unsafeToFuture()
    }

    List(true, false).foreach { synchronous =>
      s"capture ${if (synchronous) "synchronous driver" else "publisher"} failures" in {
        val calls  = new AtomicInteger()
        val error  = new IllegalStateException("list database names failed")
        val client = new LiveMongoClient[IO](SessionParityFixture.client { _ =>
          calls.incrementAndGet()
          if (synchronous) throw error
          else SearchIndexFixture.publisher[String](Nil, Some(error))
        })
        val effect = client.listDatabaseNames(session)
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
