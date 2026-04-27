---
id: find
title: "Find"
tags: ["Filter", "find", "sort", "skip", "limit", "projection"]
---

The find operation retrieves documents from a collection. It supports filtering, sorting, pagination, field projection, and streaming for large result sets.

## Basic usage

```scala
import cats.effect.IO
import mongo4cats.bson.Document
import mongo4cats.collection.MongoCollection

val collection: MongoCollection[IO, Document] = ???

// Stream all documents
val stream: fs2.Stream[IO, Document] = collection.find.stream

// Collect all documents into memory
val all: IO[Iterable[Document]] = collection.find.all

// Fetch only the first matching document
val first: IO[Option[Document]] = collection.find.first
```

## Filtering

Pass a `Filter` to restrict which documents are returned. Filters are composable with `&&` (AND), `||` (OR), and `.not`:

```scala
import mongo4cats.operations.Filter

// Equality
val byName   = Filter.eq("name", "Alice")
val byStatus = Filter.eq("status", "active")

// Comparisons
val adults  = Filter.gte("age", 18)
val recent  = Filter.gt("createdAt", Instant.parse("2024-01-01T00:00:00Z"))

// Combine with &&
val result: IO[Iterable[Document]] = collection.find(byName && byStatus).all

// Combine with ||
val eitherResult: IO[Option[Document]] = collection.find(byName || Filter.eq("name", "Bob")).first

// Negate
val notActive: IO[Iterable[Document]] = collection.find(byStatus.not).all
```

### Filter reference

| Filter | Description |
|---|---|
| `Filter.empty` | Matches all documents |
| `Filter.eq(field, value)` | Field equals value |
| `Filter.ne(field, value)` | Field not equal to value |
| `Filter.gt(field, value)` | Field greater than value |
| `Filter.gte(field, value)` | Field greater than or equal to value |
| `Filter.lt(field, value)` | Field less than value |
| `Filter.lte(field, value)` | Field less than or equal to value |
| `Filter.in(field, values)` | Field value is in the list |
| `Filter.nin(field, values)` | Field value is not in the list |
| `Filter.exists(field)` | Field is present in the document |
| `Filter.notExists(field)` | Field is absent from the document |
| `Filter.isNull(field)` | Field value is null |
| `Filter.regex(field, pattern)` | Field matches a regex pattern (String or `scala.util.matching.Regex`) |
| `Filter.text(search)` | Full-text search (requires a text index) |
| `Filter.all(field, values)` | Array field contains all the given values |
| `Filter.elemMatch(field, filter)` | At least one array element matches the filter |
| `Filter.size(field, n)` | Array field has exactly n elements |
| `Filter.typeIs(field, bsonType)` | Field is of the given BSON type |
| `Filter.mod(field, divisor, remainder)` | Modulo operation |
| `Filter.idEq(value)` | Match on `_id` |
| `Filter.and(filters*)` | Logical AND |
| `Filter.or(filters*)` | Logical OR |
| `Filter.where(jsExpr)` | Match using a JavaScript expression |
| `Filter.jsonSchema(schema)` | Match using a JSON schema |

Geospatial filters (`geoWithin`, `geoIntersects`, `near`, `nearSphere`, etc.) are also available.

## Sorting

```scala
import mongo4cats.operations.Sort

// Ascending by one field
collection.find.sort(Sort.asc("name")).all

// Descending by one field
collection.find.sort(Sort.desc("createdAt")).all

// Multi-field: ascending by "category", then descending by "score"
collection.find.sort(Sort.asc("category").desc("score")).all

// Shorthand methods on the query builder
collection.find.sortBy("name").sortByDesc("createdAt").all

// Text score sort (requires a text index and text filter)
collection.find(Filter.text("search term"))
  .sort(Sort.metaTextScore("score"))
  .all
```

## Pagination

```scala
// Skip the first 20 results and return the next 10
collection.find.skip(20).limit(10).all
```

## Projection

Use `Projection` to include or exclude specific fields, reducing the amount of data transferred:

```scala
import mongo4cats.operations.Projection

// Include only name and email (and implicitly _id)
collection.find.projection(Projection.include("name").include("email")).all

// Exclude _id, include everything else
collection.find.projection(Projection.excludeId).all

// Exclude specific fields
collection.find.projection(Projection.exclude("sensitiveField")).all

// Return a slice of an array field (skip 0, take 5)
collection.find.projection(Projection.slice("tags", 5)).all
```

## Combining options

Options can be chained in any order:

```scala
collection.find(Filter.eq("status", "active"))
  .sort(Sort.desc("score"))
  .skip(10)
  .limit(20)
  .projection(Projection.excludeId)
  .all
```

## Streaming large result sets

Use `.stream` instead of `.all` to process documents one at a time without loading the entire result set into memory:

```scala
import fs2.Stream

val stream: fs2.Stream[IO, Document] =
  collection.find(Filter.gte("score", 0)).sort(Sort.desc("score")).stream

stream.evalMap(doc => IO.println(doc)).compile.drain
```

## Atomic find-and-modify operations

`MongoCollection[F, T]` also supports atomic operations that find a document and modify it in one step:

```scala
import mongo4cats.operations.{Filter, Update}
import mongo4cats.models.collection.FindOneAndUpdateOptions

// Find the first matching document and update it, returning the document AFTER the update
val updated: IO[Option[Document]] = collection.findOneAndUpdate(
  Filter.eq("name", "Alice"),
  Update.inc("loginCount", 1),
  FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
)

// Find and delete, returning the deleted document
val deleted: IO[Option[Document]] = collection.findOneAndDelete(Filter.eq("name", "Bob"))

// Find and replace
val replaced: IO[Option[Document]] = collection.findOneAndReplace(
  Filter.eq("_id", someId),
  newDocument
)
```

## Using with a client session (transactions)

All find operations accept an optional `ClientSession[F]` as the first argument to run within a transaction:

```scala
client.startSession.use { session =>
  collection.find(session, Filter.eq("status", "pending")).all
}
```