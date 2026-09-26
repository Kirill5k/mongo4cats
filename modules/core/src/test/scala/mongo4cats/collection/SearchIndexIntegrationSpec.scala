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

import cats.effect.{IO, Resource}
import cats.effect.unsafe.implicits.global
import mongo4cats.bson.Document
import mongo4cats.bson.syntax._
import mongo4cats.client.MongoClient
import mongo4cats.models.client.{ConnectionString, MongoClientSettings}
import mongo4cats.models.collection.{SearchIndexModel, SearchIndexType}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import java.util.UUID
import java.util.concurrent.{TimeUnit, TimeoutException}
import scala.concurrent.duration._

/** Opt in with MONGO4CATS_SEARCH_URI pointing at a deployment supporting both Search and Vector Search. */
class SearchIndexIntegrationSpec extends AsyncWordSpec with Matchers {
  private val operationTimeout = 30.seconds
  private val readinessTimeout = 10.minutes
  private val searchName       = "titles"
  private val vectorName       = "embeddings"

  private val searchDefinition        = Document.parse("""{"mappings":{"dynamic":false,"fields":{"title":{"type":"string"}}}}""")
  private val updatedSearchDefinition = Document.parse(
    """{"mappings":{"dynamic":false,"fields":{"title":{"type":"string"},"category":{"type":"string"}}}}"""
  )
  private val vectorDefinition = Document.parse(
    """{"fields":[{"type":"vector","path":"embedding","numDimensions":3,"similarity":"cosine"}]}"""
  )
  private val updatedVectorDefinition = Document.parse(
    """{"fields":[{"type":"vector","path":"embedding","numDimensions":3,"similarity":"cosine"},{"type":"filter","path":"category"}]}"""
  )

  "Search index management" should {
    "create, list, update and drop Search and Vector Search indexes" in {
      val uri            = sys.env.getOrElse("MONGO4CATS_SEARCH_URI", cancel("Set MONGO4CATS_SEARCH_URI to run Search integration tests"))
      val databaseName   = sys.env.getOrElse("MONGO4CATS_SEARCH_DATABASE", "mongo4cats_search_tests")
      val collectionName = s"search_indexes_${UUID.randomUUID().toString.replace("-", "")}"
      val settings       = MongoClientSettings
        .builder()
        .applyConnectionString(ConnectionString(uri))
        .timeout(operationTimeout.toMillis, TimeUnit.MILLISECONDS)
        .build()

      val collection = for {
        client <- MongoClient.create[IO](settings)
        db     <- Resource.eval(client.getDatabase(databaseName))
        coll   <- Resource.make(db.getCollection(collectionName))(_.drop.timeout(operationTimeout))
      } yield coll

      collection
        .use { coll =>
          for {
            _ <- coll.insertOne(Document("title" := "Search lifecycle", "category" := "test", "embedding" := List(0.1, 0.2, 0.3)))
            createdSearch <- coll.createSearchIndex(searchName, searchDefinition)
            createdVector <- coll.createSearchIndexes(
              List(SearchIndexModel(vectorDefinition, Some(vectorName), SearchIndexType.vectorSearch))
            )
            _         <- awaitReady(coll, searchName)(_.getNested("mappings.fields.title").isDefined)
            _         <- awaitReady(coll, vectorName)(hasVectorField(_, "embedding", "vector"))
            listed    <- coll.listSearchIndexes
            typed     <- coll.listSearchIndexes[org.bson.Document](searchName)
            _         <- coll.updateSearchIndex(searchName, updatedSearchDefinition)
            _         <- coll.updateSearchIndex(vectorName, updatedVectorDefinition)
            _         <- awaitReady(coll, searchName)(_.getNested("mappings.fields.category").isDefined)
            _         <- awaitReady(coll, vectorName)(hasVectorField(_, "category", "filter"))
            _         <- coll.dropSearchIndex(searchName)
            _         <- coll.dropSearchIndex(vectorName)
            remaining <- await(coll.listSearchIndexes, "both indexes to be removed")(_.isEmpty)
          } yield {
            createdSearch mustBe searchName
            createdVector.toList mustBe List(vectorName)
            listed.flatMap(_.getString("name")).toSet mustBe Set(searchName, vectorName)
            typed.toList.map(_.getString("name")) mustBe List(searchName)
            remaining mustBe empty
          }
        }
        .unsafeToFuture()
    }
  }

  private def hasVectorField(definition: Document, path: String, fieldType: String): Boolean =
    definition
      .getList("fields")
      .exists(_.flatMap(_.asDocument).exists { field =>
        field.getString("path").contains(path) && field.getString("type").contains(fieldType)
      })

  private def version(document: Document, path: String): Option[Long] =
    document.getNested(path).flatMap(value => value.asLong.orElse(value.asInt.map(_.toLong)))

  private def awaitReady(collection: MongoCollection[IO, Document], name: String)(
      expectedDefinition: Document => Boolean
  ): IO[List[Document]] =
    await(collection.listSearchIndexes(name), s"$name to be ready with its current definition") { indexes =>
      indexes.exists { index =>
        val latestVersion = version(index, "latestDefinitionVersion.version")
        // An update can leave the old generation queryable while the new generation is still building.
        val activeVersionMatches = index.getList("statusDetail").exists { hosts =>
          hosts.nonEmpty && hosts.forall(_.asDocument.exists { host =>
            latestVersion.isDefined && version(host, "mainIndex.definitionVersion.version") == latestVersion
          })
        }
        index.getString("status").contains("READY") &&
        index.getBoolean("queryable").contains(true) &&
        index.getDocument("latestDefinition").exists(expectedDefinition) && activeVersionMatches
      }
    }

  private def await(fetch: IO[Iterable[Document]], description: String)(done: List[Document] => Boolean): IO[List[Document]] = {
    def loop: IO[List[Document]] = fetch.timeout(operationTimeout).flatMap { result =>
      val indexes = result.toList
      indexes.find(_.getString("status").contains("FAILED")) match {
        case Some(failed)          => IO.raiseError(new AssertionError(s"Search index build failed: ${failed.toJson}"))
        case None if done(indexes) => IO.pure(indexes)
        case None                  => IO.sleep(2.seconds) *> IO.defer(loop)
      }
    }
    loop.timeoutTo(readinessTimeout, IO.raiseError(new TimeoutException(s"Timed out waiting for $description")))
  }
}
