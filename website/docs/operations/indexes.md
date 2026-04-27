---
id: indexes
title: "Indexes"
tags: ["Index", "createIndex", "unique", "TTL"]
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