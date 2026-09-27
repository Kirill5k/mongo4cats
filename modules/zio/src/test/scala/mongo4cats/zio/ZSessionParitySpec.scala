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

import mongo4cats.client.SessionParityFixture
import mongo4cats.collection.{CollectionEffectFixture, SearchIndexFixture}
import mongo4cats.models.database.CreateCollectionOptions
import zio.{durationInt, Scope, Task, ZIO}
import zio.test._

import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}

object ZSessionParitySpec extends ZIOSpecDefault {
  private val session = CollectionEffectFixture.session[Task]
  private val options = CreateCollectionOptions(capped = true, sizeInBytes = 2048L, maxDocuments = 100L)
  private val operations: List[(String, Option[CreateCollectionOptions], ZMongoDatabase => Task[Unit])] = List(
    ("default options", None, _.createCollection(session, "events")),
    ("explicit options", Some(options), _.createCollection(session, "events", options))
  )

  override def spec: Spec[TestEnvironment with Scope, Any] = suite("Client and database session overloads")(
    suite("list database names")(
      test("defer and repeat driver invocation while forwarding the session") {
        val calls    = new AtomicInteger()
        val observed = new AtomicReference[Array[AnyRef]]()
        val client   = new ZMongoClientLive(SessionParityFixture.client { args =>
          calls.incrementAndGet()
          observed.set(args)
          SearchIndexFixture.publisher(SessionParityFixture.names)
        })
        val effect = client.listDatabaseNames(session)
        val before = calls.get()
        for {
          first      <- effect
          afterFirst <- ZIO.succeed(calls.get())
          second     <- effect
        } yield assertTrue(
          before == 0,
          first.toList == SessionParityFixture.names,
          second.toList == SessionParityFixture.names,
          afterFirst == 1,
          calls.get() == 2,
          observed.get().length == 1,
          observed.get()(0) eq session.underlying
        )
      },
      test("return an empty iterable for an empty publisher") {
        val client = new ZMongoClientLive(SessionParityFixture.client(_ => SearchIndexFixture.publisher[String](Nil)))
        client.listDatabaseNames(session).map(result => assertTrue(result.isEmpty))
      },
      suite("errors")(
        List(true, false).map { synchronous =>
          test(s"capture ${if (synchronous) "synchronous driver" else "publisher"} failures") {
            val calls  = new AtomicInteger()
            val error  = new IllegalStateException("list database names failed")
            val client = new ZMongoClientLive(SessionParityFixture.client { _ =>
              calls.incrementAndGet()
              if (synchronous) throw error
              else SearchIndexFixture.publisher[String](Nil, Some(error))
            })
            val effect = client.listDatabaseNames(session)
            val before = calls.get()
            effect.either.map(result => assertTrue(before == 0, result.left.exists(_ eq error), calls.get() == 1))
          }
        }
      )
    ),
    suite("create collection")(
      operations.map { case (name, suppliedOptions, create) =>
        suite(name)(
          test("defer and repeat driver invocation while forwarding name, session and options") {
            val calls    = new AtomicInteger()
            val observed = new AtomicReference[Array[AnyRef]]()
            val database = new ZMongoDatabaseLive(SessionParityFixture.database { args =>
              calls.incrementAndGet()
              observed.set(args)
              SearchIndexFixture.publisher[Void](Nil)
            })
            val effect = create(database)
            val before = calls.get()
            for {
              _          <- effect
              afterFirst <- ZIO.succeed(calls.get())
              _          <- effect
            } yield {
              val args = observed.get()
              assertTrue(
                before == 0,
                afterFirst == 1,
                calls.get() == 2,
                args.length == 3,
                args(0) eq session.underlying,
                args(1) == "events",
                args(2).toString == suppliedOptions.getOrElse(CreateCollectionOptions()).toString,
                suppliedOptions.forall(expected => args(2) eq expected)
              )
            }
          },
          suite("errors")(
            List(true, false).map { synchronous =>
              test(s"capture ${if (synchronous) "synchronous driver" else "publisher"} failures") {
                val calls    = new AtomicInteger()
                val error    = new IllegalStateException("create collection failed")
                val database = new ZMongoDatabaseLive(SessionParityFixture.database { _ =>
                  calls.incrementAndGet()
                  if (synchronous) throw error
                  else SearchIndexFixture.publisher[Void](Nil, Some(error))
                })
                val effect = create(database)
                val before = calls.get()
                effect.either.map(result => assertTrue(before == 0, result.left.exists(_ eq error), calls.get() == 1))
              }
            }
          )
        )
      }
    )
  ) @@ TestAspect.timeout(10.seconds)
}
