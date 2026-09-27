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
import fs2.Stream
import mongo4cats.codecs.{CodecRegistry, MongoCodecProvider}
import mongo4cats.collection.CollectionEffectFixture.session
import mongo4cats.database.DatabaseEffectFixture._
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import java.util.concurrent.atomic.AtomicInteger

class DatabaseEffectSpec extends AsyncWordSpec with Matchers {
  private val clientSession = session[IO]

  operations[IO, Stream[IO, *]](clientSession).foreach { operation =>
    s"Database ${operation.label}" should {
      "defer invocation, invoke the driver again on each run and preserve results" in {
        val calls = new AtomicInteger()
        val db    = new LiveMongoDatabase[IO](database { (method, arguments) =>
          method mustBe operation.method
          operation.verifyArguments(arguments)
          calls.incrementAndGet()
          operation.response()
        })
        val effect = operation.run(db)
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

      "capture synchronous driver exceptions in the effect" in {
        val error = new IllegalArgumentException("invalid database argument")
        val calls = new AtomicInteger()
        val db    = new LiveMongoDatabase[IO](database { (_, _) =>
          calls.incrementAndGet()
          throw error
        })
        val effect = operation.run(db)
        calls.get() mustBe 0

        effect.attempt
          .map { result =>
            result.swap.toOption.get must be theSameInstanceAs error
            calls.get() mustBe 1
          }
          .unsafeToFuture()
      }

      "preserve asynchronous publisher failures" in {
        val error = new IllegalStateException("database operation failed")
        val db    = new LiveMongoDatabase[IO](database((_, _) => operation.response(Some(error))))
        operation.run(db).attempt.map(_.swap.toOption.get must be theSameInstanceAs error).unsafeToFuture()
      }
    }
  }

  "Typed getCollection" should {
    "defer handle creation and preserve requested codecs on each run" in {
      val calls       = new AtomicInteger()
      val markerCodec = CodecInheritanceFixture.codec[CodecInheritanceFixture.Marker]
      val registry    = CodecInheritanceFixture.registry(markerCodec)
      val db          = new LiveMongoDatabase[IO](database { (method, arguments) =>
        method mustBe "getCollection"
        arguments.toList mustBe List[AnyRef]("markers", classOf[CodecInheritanceFixture.Marker])
        calls.incrementAndGet()
        CodecInheritanceFixture.database().getCollection("markers", classOf[CodecInheritanceFixture.Marker])
      })
      val effect = db.getCollection[CodecInheritanceFixture.Marker]("markers", registry)
      calls.get() mustBe 0

      (for {
        first  <- effect
        second <- effect
      } yield {
        calls.get() mustBe 2
        first.underlying must not be theSameInstanceAs(second.underlying)
        first.documentClass mustBe classOf[CodecInheritanceFixture.Marker]
        first.codecs.get(classOf[CodecInheritanceFixture.Marker]) must be theSameInstanceAs markerCodec
        second.codecs.get(classOf[CodecInheritanceFixture.Marker]) must be theSameInstanceAs markerCodec
      }).unsafeToFuture()
    }

    "capture synchronous driver failures" in {
      val error  = new IllegalArgumentException("invalid collection name")
      val db     = new LiveMongoDatabase[IO](database((_, _) => throw error))
      val effect = db.getCollection[CodecInheritanceFixture.Marker]("markers", CodecInheritanceFixture.registry())
      effect.attempt.map(_.swap.toOption.get must be theSameInstanceAs error).unsafeToFuture()
    }
  }

  "Database construction" should {
    "defer codec configuration until each execution" in {
      val calls                                                             = new AtomicInteger()
      lazy val underlying: com.mongodb.reactivestreams.client.MongoDatabase = rawDatabase { (method, _) =>
        calls.incrementAndGet()
        method match {
          case "getCodecRegistry"  => CodecRegistry.Default
          case "withCodecRegistry" => underlying
          case other               => throw new AssertionError(s"Unexpected factory call: $other")
        }
      }
      val effect = MongoDatabase.make[IO](underlying)
      calls.get() mustBe 0

      (for {
        _          <- effect
        afterFirst <- IO(calls.get())
        _          <- effect
      } yield {
        afterFirst mustBe 2
        calls.get() mustBe 4
      }).unsafeToFuture()
    }

    "capture codec configuration failures in the effect" in {
      val error  = new IllegalArgumentException("invalid database codecs")
      val effect = MongoDatabase.make[IO](rawDatabase((_, _) => throw error))
      effect.attempt.map(_.swap.toOption.get must be theSameInstanceAs error).unsafeToFuture()
    }
  }

  "Collection lookup conveniences" should {
    "defer and capture failures reading the database codecs" in {
      val error = new IllegalArgumentException("database codec lookup failed")
      val calls = new AtomicInteger()
      val db    = new LiveMongoDatabase[IO](rawDatabase { (method, _) =>
        method mustBe "getCodecRegistry"
        calls.incrementAndGet()
        throw error
      })
      val effect = db.getCollection("documents")
      calls.get() mustBe 0

      effect.attempt
        .map { result =>
          result.swap.toOption.get must be theSameInstanceAs error
          calls.get() mustBe 1
        }
        .unsafeToFuture()
    }

    "defer and capture codec provider failures" in {
      val error                                                                 = new IllegalArgumentException("codec provider failed")
      val calls                                                                 = new AtomicInteger()
      implicit val provider: MongoCodecProvider[CodecInheritanceFixture.Marker] = new MongoCodecProvider[CodecInheritanceFixture.Marker] {
        override def get: org.bson.codecs.configuration.CodecProvider = {
          calls.incrementAndGet()
          throw error
        }
      }
      val db     = new LiveMongoDatabase[IO](database((_, _) => throw new AssertionError("Driver should not be called")))
      val effect = db.getCollectionWithCodec[CodecInheritanceFixture.Marker]("markers")
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
