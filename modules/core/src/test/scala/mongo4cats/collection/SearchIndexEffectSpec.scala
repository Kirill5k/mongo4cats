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
import mongo4cats.bson.Document
import mongo4cats.collection.SearchIndexFixture._
import mongo4cats.errors.MongoEmptyStreamException
import mongo4cats.models.collection.SearchIndexModel
import org.reactivestreams.Publisher
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}
import scala.concurrent.duration._

class SearchIndexEffectSpec extends AsyncWordSpec with Matchers {
  private case class Operation(
      label: String,
      method: String,
      arguments: List[AnyRef],
      values: List[AnyRef],
      result: List[Any],
      run: MongoCollection[IO, Document] => IO[List[Any]],
      name: Option[String] = None
  ) {
    def response(source: Publisher[AnyRef], onName: String => Unit = _ => ()): AnyRef =
      if (method == "listSearchIndexes") listing(source, onName) else source
  }

  private val operations = List(
    Operation(
      "unnamed creation",
      "createSearchIndex",
      List(definition),
      List("search"),
      List("search"),
      _.createSearchIndex(definition).map(List(_))
    ),
    Operation(
      "named creation",
      "createSearchIndex",
      List("search", definition),
      List("search"),
      List("search"),
      _.createSearchIndex("search", definition).map(List(_))
    ),
    Operation(
      "batch creation",
      "createSearchIndexes",
      List(forwardedIndexes(indexes)),
      names,
      names,
      _.createSearchIndexes(indexes).map(_.toList)
    ),
    Operation("default listing", "listSearchIndexes", Nil, metadata, metadata.map(Document.fromJava), _.listSearchIndexes.map(_.toList)),
    Operation(
      "named listing",
      "listSearchIndexes",
      Nil,
      metadata,
      metadata.map(Document.fromJava),
      _.listSearchIndexes("search").map(_.toList),
      Some("search")
    ),
    Operation(
      "typed listing",
      "listSearchIndexes",
      List(classOf[Metadata]),
      typedMetadata,
      typedMetadata,
      _.listSearchIndexes[Metadata].map(_.toList)
    ),
    Operation(
      "named typed listing",
      "listSearchIndexes",
      List(classOf[Metadata]),
      typedMetadata,
      typedMetadata,
      _.listSearchIndexes[Metadata]("search").map(_.toList),
      Some("search")
    ),
    Operation("update", "updateSearchIndex", List("search", definition), Nil, Nil, _.updateSearchIndex("search", definition).as(Nil)),
    Operation("drop", "dropSearchIndex", List("search"), Nil, Nil, _.dropSearchIndex("search").as(Nil))
  )

  operations.foreach { operation =>
    s"Search index ${operation.label}" should {
      "defer invocation and construct a fresh, independently filtered publisher on each execution" in {
        val calls        = new AtomicInteger()
        val filterCalls  = new AtomicInteger()
        val observedName = new AtomicReference[String]()
        val coll         = new LiveMongoCollection[IO, Document](collection { (_, _) =>
          calls.incrementAndGet()
          val publisherFilters = new AtomicInteger()
          operation.response(
            publisher(operation.values),
            name => {
              if (publisherFilters.incrementAndGet() != 1) throw new IllegalStateException("Listing publisher was reused")
              observedName.set(name)
              filterCalls.incrementAndGet()
              ()
            }
          )
        })
        val effect = operation.run(coll)
        calls.get() mustBe 0
        filterCalls.get() mustBe 0

        (for {
          first      <- effect
          afterFirst <- IO(calls.get())
          second     <- effect
        } yield {
          first mustBe operation.result
          second mustBe operation.result
          afterFirst mustBe 1
          calls.get() mustBe 2
          filterCalls.get() mustBe operation.name.fold(0)(_ => 2)
          Option(observedName.get()) mustBe operation.name
        }).unsafeToFuture()
      }

      "forward the exact arguments and preserve all returned values in order" in {
        val observed     = new AtomicReference[(String, List[AnyRef])]()
        val observedName = new AtomicReference[String]()
        val coll         = new LiveMongoCollection[IO, Document](collection { (method, args) =>
          observed.set((method, normalizeArguments(args)))
          operation.response(publisher(operation.values), observedName.set)
        })

        operation
          .run(coll)
          .map { result =>
            observed.get() mustBe ((operation.method, operation.arguments))
            Option(observedName.get()) mustBe operation.name
            result mustBe operation.result
          }
          .unsafeToFuture()
      }

      "capture synchronous driver exceptions inside the effect without changing them" in {
        val error = new IllegalStateException("driver invocation failed")
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

      "preserve publisher errors" in {
        val error = new IllegalArgumentException("server rejected search operation")
        val coll  = new LiveMongoCollection[IO, Document](collection { (_, _) =>
          operation.response(publisher[AnyRef](Nil, Some(error)))
        })

        operation.run(coll).attempt.map(_.swap.toOption.get must be theSameInstanceAs error).unsafeToFuture()
      }

      "handle empty publishers using the existing scalar, iterable and void conventions" in {
        val coll = new LiveMongoCollection[IO, Document](collection { (_, _) =>
          operation.response(publisher[AnyRef](Nil))
        })

        operation
          .run(coll)
          .attempt
          .map { result =>
            if (operation.method == "createSearchIndex") result mustBe Left(MongoEmptyStreamException)
            else result mustBe Right(Nil)
          }
          .unsafeToFuture()
      }

      "cancel an active subscription without waiting for the server to complete" in {
        val source = new ControlledPublisher
        val coll   = new LiveMongoCollection[IO, Document](collection((_, _) => operation.response(source)))

        (for {
          fiber        <- operation.run(coll).start
          _            <- IO.fromCompletableFuture(IO.pure(source.requested)).timeout(3.seconds)
          cancellation <- fiber.cancel.start
          _            <- cancellation.joinWithNever.timeout(3.seconds)
        } yield source.cancelCalls.get() mustBe 1)
          .guarantee(IO(source.finish()))
          .unsafeToFuture()
      }
    }
  }

  operations.filter(_.name.nonEmpty).foreach { operation =>
    s"Search index ${operation.label}" should {
      "capture synchronous name-filter failures inside the effect" in {
        val error = new IllegalArgumentException("invalid index name")
        val calls = new AtomicInteger()
        val coll  = new LiveMongoCollection[IO, Document](collection { (_, _) =>
          operation.response(
            publisher(operation.values),
            _ => {
              calls.incrementAndGet()
              throw error
            }
          )
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
    }
  }

  "Search index batch creation" should {
    "route a single vector index through the batch driver method" in {
      val vector   = indexes.last
      val observed = new AtomicReference[(String, List[AnyRef])]()
      val coll     = new LiveMongoCollection[IO, Document](collection { (method, args) =>
        observed.set((method, normalizeArguments(args)))
        publisher(List("vectors"))
      })

      coll
        .createSearchIndexes(List(vector))
        .map { result =>
          observed.get() mustBe (("createSearchIndexes", List(forwardedIndexes(List(vector)))))
          result.toList mustBe List("vectors")
        }
        .unsafeToFuture()
    }

    "capture model validation during execution without calling the driver" in {
      val invalid = SearchIndexModel(null, Some("invalid"))
      val calls   = new AtomicInteger()
      val coll    = new LiveMongoCollection[IO, Document](collection { (_, _) =>
        calls.incrementAndGet()
        publisher(List("invalid"))
      })
      val effect = coll.createSearchIndexes(List(invalid))
      calls.get() mustBe 0

      effect.attempt
        .map { result =>
          result.swap.toOption.get mustBe an[IllegalArgumentException]
          calls.get() mustBe 0
        }
        .unsafeToFuture()
    }
  }
}
