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
import mongo4cats.codecs.{CodecRegistry, MongoCodecProvider}
import mongo4cats.database.CodecInheritanceFixture
import mongo4cats.database.DatabaseEffectFixture._
import zio.{durationInt, Scope, Task, ZIO}
import zio.stream.Stream
import zio.test._

import java.util.concurrent.atomic.AtomicInteger

object ZDatabaseEffectSpec extends ZIOSpecDefault {
  private val clientSession = session[Task]

  override def spec: Spec[TestEnvironment with Scope, Any] = suite("Database effects")(
    suite("operation contracts")(
      operations[Task, Stream[Throwable, *]](clientSession).map { operation =>
        suite(operation.label)(
          test("defer invocation, invoke the driver again on each run and preserve results") {
            val calls = new AtomicInteger()
            val db    = new ZMongoDatabaseLive(database { (method, arguments) =>
              require(method == operation.method, s"Unexpected driver method: $method")
              operation.verifyArguments(arguments)
              calls.incrementAndGet()
              operation.response()
            })
            val effect = operation.run(db)
            val before = calls.get()

            for {
              first      <- effect
              afterFirst <- ZIO.succeed(calls.get())
              second     <- effect
            } yield assertTrue(before == 0, afterFirst == 1, calls.get() == 2, first == operation.expected, second == operation.expected)
          },
          test("capture synchronous driver exceptions in the typed error channel") {
            val error = new IllegalArgumentException("invalid database argument")
            val calls = new AtomicInteger()
            val db    = new ZMongoDatabaseLive(database { (_, _) =>
              calls.incrementAndGet()
              throw error
            })
            val effect = operation.run(db)
            val before = calls.get()

            effect.either.map(result => assertTrue(before == 0, result.left.exists(_ eq error), calls.get() == 1))
          },
          test("preserve asynchronous publisher failures") {
            val error = new IllegalStateException("database operation failed")
            val db    = new ZMongoDatabaseLive(database((_, _) => operation.response(Some(error))))
            operation.run(db).either.map(result => assertTrue(result.left.exists(_ eq error)))
          }
        )
      }
    ),
    suite("typed getCollection")(
      test("defer handle creation and preserve requested codecs on each run") {
        val calls       = new AtomicInteger()
        val markerCodec = CodecInheritanceFixture.codec[CodecInheritanceFixture.Marker]
        val registry    = CodecInheritanceFixture.registry(markerCodec)
        val db          = new ZMongoDatabaseLive(database { (method, arguments) =>
          require(method == "getCollection")
          require(arguments.toList == List[AnyRef]("markers", classOf[CodecInheritanceFixture.Marker]))
          calls.incrementAndGet()
          CodecInheritanceFixture.database().getCollection("markers", classOf[CodecInheritanceFixture.Marker])
        })
        val effect = db.getCollection[CodecInheritanceFixture.Marker]("markers", registry)
        val before = calls.get()

        for {
          first  <- effect
          second <- effect
        } yield assertTrue(
          before == 0,
          calls.get() == 2,
          first.underlying ne second.underlying,
          first.documentClass == classOf[CodecInheritanceFixture.Marker],
          first.codecs.get(classOf[CodecInheritanceFixture.Marker]) eq markerCodec,
          second.codecs.get(classOf[CodecInheritanceFixture.Marker]) eq markerCodec
        )
      },
      test("capture synchronous driver failures in the typed error channel") {
        val error  = new IllegalArgumentException("invalid collection name")
        val db     = new ZMongoDatabaseLive(database((_, _) => throw error))
        val effect = db.getCollection[CodecInheritanceFixture.Marker]("markers", CodecInheritanceFixture.registry())
        effect.either.map(result => assertTrue(result.left.exists(_ eq error)))
      }
    ),
    suite("database construction")(
      test("defer codec configuration until each execution") {
        val calls                                                             = new AtomicInteger()
        lazy val underlying: com.mongodb.reactivestreams.client.MongoDatabase = rawDatabase { (method, _) =>
          calls.incrementAndGet()
          method match {
            case "getCodecRegistry"  => CodecRegistry.Default
            case "withCodecRegistry" => underlying
            case other               => throw new AssertionError(s"Unexpected factory call: $other")
          }
        }
        val effect = ZMongoDatabase.make(underlying)
        val before = calls.get()

        for {
          _          <- effect
          afterFirst <- ZIO.succeed(calls.get())
          _          <- effect
        } yield assertTrue(before == 0, afterFirst == 2, calls.get() == 4)
      },
      test("capture codec configuration failures in the typed error channel") {
        val error  = new IllegalArgumentException("invalid database codecs")
        val effect = ZMongoDatabase.make(rawDatabase((_, _) => throw error))
        effect.either.map(result => assertTrue(result.left.exists(_ eq error)))
      }
    ),
    suite("collection lookup conveniences")(
      test("defer and capture failures reading the database codecs") {
        val error = new IllegalArgumentException("database codec lookup failed")
        val calls = new AtomicInteger()
        val db    = new ZMongoDatabaseLive(rawDatabase { (method, _) =>
          require(method == "getCodecRegistry")
          calls.incrementAndGet()
          throw error
        })
        val effect = db.getCollection("documents")
        val before = calls.get()

        effect.either.map(result => assertTrue(before == 0, result.left.exists(_ eq error), calls.get() == 1))
      },
      test("defer and capture codec provider failures") {
        val error                                                                 = new IllegalArgumentException("codec provider failed")
        val calls                                                                 = new AtomicInteger()
        implicit val provider: MongoCodecProvider[CodecInheritanceFixture.Marker] = new MongoCodecProvider[CodecInheritanceFixture.Marker] {
          override def get: org.bson.codecs.configuration.CodecProvider = {
            calls.incrementAndGet()
            throw error
          }
        }
        val db     = new ZMongoDatabaseLive(database((_, _) => throw new AssertionError("Driver should not be called")))
        val effect = db.getCollectionWithCodec[CodecInheritanceFixture.Marker]("markers")
        val before = calls.get()

        effect.either.map(result => assertTrue(before == 0, result.left.exists(_ eq error), calls.get() == 1))
      }
    )
  ) @@ TestAspect.timeout(10.seconds)
}
