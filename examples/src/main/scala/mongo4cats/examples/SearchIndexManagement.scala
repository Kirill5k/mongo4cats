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

package mongo4cats.examples

import cats.effect.IO
import mongo4cats.bson.Document
import mongo4cats.bson.syntax._
import mongo4cats.collection.{GenericMongoCollection, MongoCollection}
import mongo4cats.models.collection.{SearchIndexModel, SearchIndexType}
import mongo4cats.zio.ZMongoCollection
import zio.Task

/** Call these effects with a collection from a Search-enabled deployment. Compiling this example performs no remote operations. */
object SearchIndexManagement {

  val searchDefinition: Document = Document("mappings" := Document("dynamic" := true))

  val vectorDefinition: Document = Document(
    "fields" := List(
      Document(
        "type"          := "vector",
        "path"          := "embedding",
        "numDimensions" := 1536,
        "similarity"    := "cosine"
      )
    )
  )

  val indexes: Seq[SearchIndexModel] = Seq(
    SearchIndexModel(searchDefinition, name = Some("description_search")),
    SearchIndexModel(vectorDefinition, name = Some("vector_index"), indexType = SearchIndexType.vectorSearch)
  )

  // Both backends expose the same management API. A single vector index also uses createSearchIndexes.
  def createWithCats(collection: MongoCollection[IO, Document]): IO[Iterable[String]] =
    collection.createSearchIndexes(indexes)

  def createWithZio(collection: ZMongoCollection[Document]): Task[Iterable[String]] =
    collection.createSearchIndexes(indexes)

  // Singular creation is only for ordinary Search indexes. These are alternatives to the batch above.
  def createDefault[F[_], T, S[_]](collection: GenericMongoCollection[F, T, S]): F[String] =
    collection.createSearchIndex(searchDefinition)

  def createNamed[F[_], T, S[_]](collection: GenericMongoCollection[F, T, S]): F[String] =
    collection.createSearchIndex("description_search", searchDefinition)

  def listWithCats(collection: MongoCollection[IO, Document]): IO[Iterable[Document]] =
    collection.listSearchIndexes

  def listWithZio(collection: ZMongoCollection[Document]): Task[Iterable[Document]] =
    collection.listSearchIndexes("vector_index")

  // The Java Document codec is already available; custom result classes need a registered codec.
  def listTyped[F[_], T, S[_]](collection: GenericMongoCollection[F, T, S]): F[Iterable[org.bson.Document]] =
    collection.listSearchIndexes[org.bson.Document]

  def listNamedTyped[F[_], T, S[_]](collection: GenericMongoCollection[F, T, S]): F[Iterable[org.bson.Document]] =
    collection.listSearchIndexes[org.bson.Document]("vector_index")

  // This is one readiness observation. An application can repeat it with a timeout and failure handling.
  def vectorReady(collection: MongoCollection[IO, Document]): IO[Boolean] =
    collection
      .listSearchIndexes("vector_index")
      .map(_.exists { index =>
        index.getString("status").contains("READY") && index.getBoolean("queryable").contains(true)
      })

  // Call after the index is ready. The full replacement definition can trigger another build.
  def update[F[_], T, S[_]](collection: GenericMongoCollection[F, T, S]): F[Unit] =
    collection.updateSearchIndex(
      "description_search",
      Document("analyzer" := "lucene.simple", "mappings" := Document("dynamic" := true))
    )

  // Dropping is asynchronous too: listing by name eventually becomes empty.
  def drop[F[_], T, S[_]](collection: GenericMongoCollection[F, T, S]): F[Unit] =
    collection.dropSearchIndex("vector_index")
}
