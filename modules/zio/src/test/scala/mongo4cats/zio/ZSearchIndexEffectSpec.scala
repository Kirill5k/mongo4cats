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
import mongo4cats.collection.SearchIndexFixture._
import mongo4cats.errors.MongoEmptyStreamException
import mongo4cats.models.collection.SearchIndexModel
import org.reactivestreams.Publisher
import zio.{durationInt, Scope, Task, ZIO}
import zio.test._

import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}

object ZSearchIndexEffectSpec extends ZIOSpecDefault {
  private case class Operation(
      label: String,
      method: String,
      arguments: List[AnyRef],
      values: List[AnyRef],
      result: List[Any],
      run: ZMongoCollection[Document] => Task[List[Any]],
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

  override def spec: Spec[TestEnvironment with Scope, Any] = suite("Search index effects")(
    suite("operation contracts")(
      operations.map { operation =>
        suite(operation.label)(
          test("defer invocation and construct a fresh, independently filtered publisher on every execution") {
            val calls        = new AtomicInteger()
            val filterCalls  = new AtomicInteger()
            val observedName = new AtomicReference[String]()
            val coll         = new ZMongoCollectionLive[Document](collection { (_, _) =>
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
            val effect        = operation.run(coll)
            val before        = calls.get()
            val filtersBefore = filterCalls.get()

            for {
              first      <- effect
              afterFirst <- ZIO.succeed(calls.get())
              second     <- effect
            } yield assertTrue(
              before == 0,
              filtersBefore == 0,
              afterFirst == 1,
              calls.get() == 2,
              filterCalls.get() == operation.name.fold(0)(_ => 2),
              Option(observedName.get()) == operation.name,
              first == operation.result,
              second == operation.result
            )
          },
          test("forward the exact arguments and preserve all returned values in order") {
            val observed     = new AtomicReference[(String, List[AnyRef])]()
            val observedName = new AtomicReference[String]()
            val coll         = new ZMongoCollectionLive[Document](collection { (method, args) =>
              observed.set((method, normalizeArguments(args)))
              operation.response(publisher(operation.values), observedName.set)
            })

            operation.run(coll).map { result =>
              assertTrue(
                observed.get() == ((operation.method, operation.arguments)),
                Option(observedName.get()) == operation.name,
                result == operation.result
              )
            }
          },
          test("capture synchronous driver exceptions inside the effect without changing them") {
            val error = new IllegalStateException("driver invocation failed")
            val calls = new AtomicInteger()
            val coll  = new ZMongoCollectionLive[Document](collection { (_, _) =>
              calls.incrementAndGet()
              throw error
            })
            val effect = operation.run(coll)
            val before = calls.get()

            effect.either.map(result => assertTrue(before == 0, result.left.exists(_ eq error), calls.get() == 1))
          },
          test("preserve publisher errors") {
            val error = new IllegalArgumentException("server rejected search operation")
            val coll  = new ZMongoCollectionLive[Document](collection { (_, _) =>
              operation.response(publisher[AnyRef](Nil, Some(error)))
            })

            operation.run(coll).either.map(result => assertTrue(result.left.exists(_ eq error)))
          },
          test("handle empty publishers using the existing scalar, iterable and void conventions") {
            val coll = new ZMongoCollectionLive[Document](collection { (_, _) =>
              operation.response(publisher[AnyRef](Nil))
            })

            operation.run(coll).either.map { result =>
              if (operation.method == "createSearchIndex") assertTrue(result == Left(MongoEmptyStreamException))
              else assertTrue(result == Right(Nil))
            }
          },
          test("cancel an active subscription without waiting for the server to complete") {
            val source = new ControlledPublisher
            val coll   = new ZMongoCollectionLive[Document](collection((_, _) => operation.response(source)))

            (for {
              fiber <- operation.run(coll).fork
              _     <- ZIO.fromCompletionStage(source.requested)
              exit  <- fiber.interrupt
            } yield assertTrue(exit.isInterrupted, source.cancelCalls.get() == 1))
              .ensuring(ZIO.succeed(source.finish()))
          }
        )
      }
    ),
    suite("named listing failures")(
      operations.filter(_.name.nonEmpty).map { operation =>
        test(s"${operation.label} captures synchronous name-filter failures inside the effect") {
          val error = new IllegalArgumentException("invalid index name")
          val calls = new AtomicInteger()
          val coll  = new ZMongoCollectionLive[Document](collection { (_, _) =>
            operation.response(
              publisher(operation.values),
              _ => {
                calls.incrementAndGet()
                throw error
              }
            )
          })
          val effect = operation.run(coll)
          val before = calls.get()

          effect.either.map(result => assertTrue(before == 0, result.left.exists(_ eq error), calls.get() == 1))
        }
      }
    ),
    test("route a single vector index through the batch driver method") {
      val vector   = indexes.last
      val observed = new AtomicReference[(String, List[AnyRef])]()
      val coll     = new ZMongoCollectionLive[Document](collection { (method, args) =>
        observed.set((method, normalizeArguments(args)))
        publisher(List("vectors"))
      })

      coll.createSearchIndexes(List(vector)).map { result =>
        assertTrue(
          observed.get() == (("createSearchIndexes", List(forwardedIndexes(List(vector))))),
          result.toList == List("vectors")
        )
      }
    },
    test("capture model validation during execution without calling the driver") {
      val invalid = SearchIndexModel(null, Some("invalid"))
      val calls   = new AtomicInteger()
      val coll    = new ZMongoCollectionLive[Document](collection { (_, _) =>
        calls.incrementAndGet()
        publisher(List("invalid"))
      })
      val effect = coll.createSearchIndexes(List(invalid))
      val before = calls.get()

      effect.either.map { result =>
        assertTrue(before == 0, result.left.exists(_.isInstanceOf[IllegalArgumentException]), calls.get() == 0)
      }
    }
  ) @@ TestAspect.timeout(10.seconds)
}
