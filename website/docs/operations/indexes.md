---
id: indexes
title: "Indexes"
tags: ["Index", "createIndex", "unique", "TTL", "Search", "Vector Search"]
---

Indexes make queries efficient by allowing MongoDB to find documents without scanning the entire collection. They also enable unique constraints, TTL (time-to-live) document expiry, geospatial queries, text search, and more.

See the [official MongoDB documentation](https://docs.mongodb.com/manual/indexes/) for a full conceptual overview.

## Creating indexes

`createIndex` returns the name of the created index as `F[String]`.

### Single-field index

```scala
import cats.effect.IO
import mongo4cats.operations.Index

// Ascending index on one field
val name: IO[String] = collection.createIndex(Index.ascending("email"))

// Descending index
val name2: IO[String] = collection.createIndex(Index.descending("createdAt"))
```

### Compound index

Combine multiple fields into one index. The sort direction matters for sort-covered queries:

```scala
// Ascending on "category", then descending on "score"
val compound = Index.ascending("category").descending("score")
collection.createIndex(compound)

// Using combinedWith
val i1 = Index.ascending("category")
val i2 = Index.descending("score")
collection.createIndex(i1.combinedWith(i2))
```

### Index options

Use `IndexOptions` to configure additional index properties:

```scala
import mongo4cats.models.collection.IndexOptions

// Unique index — enforces no duplicate values
collection.createIndex(
  Index.ascending("email"),
  IndexOptions().unique(true)
)

// Partial index — only index documents matching a filter
import mongo4cats.operations.Filter
collection.createIndex(
  Index.ascending("score"),
  IndexOptions().partialFilterExpression(Filter.exists("score").toBson)
)

// TTL index — documents expire after the given number of seconds
collection.createIndex(
  Index.ascending("createdAt"),
  IndexOptions().expireAfter(30, java.util.concurrent.TimeUnit.DAYS)
)

// Sparse index — omit documents where the field is missing
collection.createIndex(
  Index.ascending("optionalField"),
  IndexOptions().sparse(true)
)

// Custom index name
collection.createIndex(
  Index.ascending("name").ascending("email"),
  IndexOptions().name("name_email_idx")
)
```

### Text index (for full-text search)

`Index.text` creates a regular MongoDB text index for `Filter.text` (`$text`). For the indexes used by `$search`, `$searchMeta`, and `$vectorSearch`, see [Search and vector-search indexes](#search-and-vector-search-indexes).

```scala
collection.createIndex(Index.text("description"))

// Then query with Filter.text
collection.find(Filter.text("functional programming")).all
```

### Hashed index

```scala
collection.createIndex(Index.hashed("userId"))
```

### Geospatial index

```scala
// 2dsphere for GeoJSON data
collection.createIndex(Index.geo2dsphere("location"))

// 2d for legacy coordinate pairs
collection.createIndex(Index.geo2d("location"))
```

### Using the Java driver builders directly

The standard MongoDB Java driver index builders are also accepted:

```scala
import com.mongodb.client.model.Indexes

val index = Indexes.compoundIndex(
  Indexes.ascending("field1"),
  Indexes.descending("field2")
)
collection.createIndex(index)
```

## Listing indexes

```scala
val indexes: IO[Iterable[Document]] = collection.listIndexes

// Typed — if the index documents map to a case class
val indexes: IO[Iterable[MyIndexInfo]] = collection.listIndexes[MyIndexInfo]
```

## Dropping indexes

```scala
// Drop by name
collection.dropIndex("email_1")

// Drop by index specification
collection.dropIndex(Index.ascending("email"))

// Drop all non-_id indexes
collection.dropIndexes
```

## Search and vector-search indexes

`MongoCollection` and `ZMongoCollection` can create, list, update, and drop the indexes used by the [Search aggregation stages](aggregate.md#atlas-search-and-vector-search). These have separate management methods from regular indexes: use `listSearchIndexes` and `dropSearchIndex` for Search indexes.

Use a Search-enabled deployment that supports the management commands, such as MongoDB Atlas with Search and Vector Search available. The embedded MongoDB helpers run only `mongod` and cannot provide Search. Consult your deployment's support requirements before using these operations. With access control enabled, the database user needs the relevant privileges on the database or collection: [`createSearchIndexes`](https://www.mongodb.com/docs/manual/reference/command/createsearchindexes/#access-control), [`listSearchIndexes`](https://www.mongodb.com/docs/manual/reference/operator/aggregation/listsearchindexes/#access-control), [`updateSearchIndex`](https://www.mongodb.com/docs/manual/reference/command/updatesearchindex/#access-control), and [`dropSearchIndex`](https://www.mongodb.com/docs/manual/reference/command/dropSearchIndex/#access-control).

### Creating Search indexes

Definitions accept any `org.bson.conversions.Bson`, including mongo4cats `Document`. Pass the definition itself, without an enclosing `definition` field:

```scala
import cats.effect.IO
import mongo4cats.bson.Document
import mongo4cats.bson.syntax._
import mongo4cats.models.collection.{SearchIndexModel, SearchIndexType}

val searchDefinition = Document("mappings" := Document("dynamic" := true))

// Choose one: use the server's default name, or supply a name.
val defaultIndex: IO[String] = collection.createSearchIndex(searchDefinition)
val namedIndex: IO[String] = collection.createSearchIndex("description_search", searchDefinition)
```

To create multiple Search indexes or any vector-search index, use `createSearchIndexes`. Even one vector index requires this method; the singular method creates only Search indexes. [MongoDB driver documentation](https://www.mongodb.com/docs/drivers/java/sync/current/indexes/#mongodb-search-and-vector-search-indexes)

```scala
val vectorDefinition = Document(
  "fields" := List(
    Document(
      "type" := "vector",
      "path" := "embedding",
      "numDimensions" := 1536,
      "similarity" := "cosine"
    )
  )
)

val names: IO[Iterable[String]] = collection.createSearchIndexes(Seq(
  SearchIndexModel(searchDefinition, name = Some("description_search")),
  SearchIndexModel(
    vectorDefinition,
    name = Some("vector_index"),
    indexType = SearchIndexType.vectorSearch
  )
))
```

`SearchIndexModel` is an immutable Scala case class owned by mongo4cats, with `definition`, `name`, and `indexType` fields. It defaults to `name = None` and `indexType = SearchIndexType.search`, and is converted to the Java driver model when the creation effect runs. `SearchIndexType` remains an alias for the Java driver type. Set `numDimensions` to the size of your embedding vectors. Definition validation is performed by the driver and server.

### Listing and checking readiness

**Completing a management effect means that the server accepted the operation. It does not mean an index has finished building.** Inspect the returned metadata before querying a newly created index. [Creation behavior](https://www.mongodb.com/docs/manual/reference/command/createsearchindexes/#behavior)

```scala
val all: IO[Iterable[Document]] = collection.listSearchIndexes
val named: IO[Iterable[Document]] = collection.listSearchIndexes("vector_index")

val ready: IO[Boolean] = collection.listSearchIndexes("vector_index").map(_.exists { index =>
  index.getString("status").contains("READY") && index.getBoolean("queryable").contains(true)
})

// Typed listing uses a ClassTag and the collection's codec registry.
val typed: IO[Iterable[org.bson.Document]] = collection.listSearchIndexes[org.bson.Document]
val typedNamed: IO[Iterable[org.bson.Document]] =
  collection.listSearchIndexes[org.bson.Document]("vector_index")
```

Custom result types require a registered codec. Default listing preserves the server's metadata, including `status`, `queryable`, `latestDefinition`, and `statusDetail`. Listing by an absent name produces an empty result. Applications that wait for readiness should use a bounded retry policy and inspect failure status; mongo4cats does not poll automatically. See the [listing output reference](https://www.mongodb.com/docs/manual/reference/operator/aggregation/listsearchindexes/#output) for the fields and their meanings.

### Updating and dropping

Supply the complete replacement definition when updating either index type:

```scala
val replacementSearchDefinition = Document(
  "analyzer" := "lucene.simple",
  "mappings" := Document("dynamic" := true)
)
val replacementVectorDefinition = Document(
  "fields" := List(
    Document("type" := "vector", "path" := "embedding", "numDimensions" := 1536, "similarity" := "dotProduct")
  )
)

val updateSearch: IO[Unit] = collection.updateSearchIndex("description_search", replacementSearchDefinition)
val updateVector: IO[Unit] = collection.updateSearchIndex("vector_index", replacementVectorDefinition)

val dropSearch: IO[Unit] = collection.dropSearchIndex("description_search")
val dropVector: IO[Unit] = collection.dropSearchIndex("vector_index")
```

An update triggers a rebuild, during which the old definition may remain queryable. Check both readiness and the expected definition/version before relying on an update; `queryable = true` alone can refer to the old index. [Update behavior](https://www.mongodb.com/docs/manual/reference/command/updatesearchindex/#behavior)

Dropping is also asynchronous: wait until listing by name is empty if deletion must be complete before continuing. [Drop behavior](https://www.mongodb.com/docs/manual/reference/command/dropSearchIndex/#behavior)

The same calls on `ZMongoCollection` return `Task` instead of `F`. Driver errors remain in the effect's error channel. Search-management methods do not accept client sessions because the underlying driver provides no session overloads. Custom subclasses or test doubles of `GenericMongoCollection` must implement the new abstract Search-management methods.

The compilable [`SearchIndexManagement` example](https://github.com/Kirill5k/mongo4cats/blob/master/examples/src/main/scala/mongo4cats/examples/SearchIndexManagement.scala) demonstrates Cats Effect and ZIO usage without connecting to a deployment when compiled.

### Integration testing

The opt-in lifecycle suite creates isolated test collections and exercises both index types. Set `MONGO4CATS_SEARCH_URI` to a Search-enabled connection string and optionally `MONGO4CATS_SEARCH_DATABASE` (default: `mongo4cats_search_tests`), then run:

```sh
sbt 'core/testOnly mongo4cats.collection.SearchIndexIntegrationSpec'
```

The database user also needs permission to create, populate, and drop test collections. The suite uses bounded polling and cleans up its collections. Without `MONGO4CATS_SEARCH_URI`, it is skipped so normal CI does not need external credentials.
