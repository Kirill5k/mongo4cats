---
id: update
title: "Update"
tags: ["Update", "updateOne", "updateMany", "replaceOne", "upsert"]
---

Update operations modify fields and values in existing documents. mongo4cats provides a fluent, chainable `Update` builder that compiles to the standard MongoDB update operators.

## updateOne and updateMany

`updateOne` modifies the first document matching the filter; `updateMany` modifies all matching documents.

```scala
import cats.effect.IO
import com.mongodb.client.result.UpdateResult
import mongo4cats.operations.{Filter, Update}
import mongo4cats.models.collection.UpdateOptions

val update = Update
  .set("status", "active")
  .currentDate("updatedAt")

// Update the first matching document
val result1: IO[UpdateResult] = collection.updateOne(Filter.eq("name", "Alice"), update)

// Update all matching documents
val result2: IO[UpdateResult] = collection.updateMany(Filter.eq("status", "inactive"), update)
```

### Upsert

Pass `UpdateOptions().upsert(true)` to insert a document if no match is found:

```scala
val result: IO[UpdateResult] = collection.updateOne(
  Filter.eq("name", "Charlie"),
  Update.set("name", "Charlie").set("score", 0),
  UpdateOptions().upsert(true)
)
```

## Update operator reference

All methods return a new `Update` instance and can be chained freely:

### Field updates

| Method | MongoDB operator | Description |
|---|---|---|
| `set(field, value)` | `$set` | Set a field to a value |
| `unset(field)` | `$unset` | Remove a field |
| `rename(field, newName)` | `$rename` | Rename a field |
| `setOnInsert(field, value)` | `$setOnInsert` | Set on upsert-insert only |

### Numeric updates

| Method | MongoDB operator | Description |
|---|---|---|
| `inc(field, n)` | `$inc` | Increment a numeric field by `n` |
| `mul(field, n)` | `$mul` | Multiply a numeric field by `n` |
| `min(field, value)` | `$min` | Set field to `value` if `value < current` |
| `max(field, value)` | `$max` | Set field to `value` if `value > current` |

### Date updates

| Method | MongoDB operator | Description |
|---|---|---|
| `currentDate(field)` | `$currentDate` | Set field to current date (BSON Date) |
| `currentTimestamp(field)` | `$currentDate` | Set field to current timestamp |

### Array updates

| Method | MongoDB operator | Description |
|---|---|---|
| `push(field, value)` | `$push` | Append a value to an array |
| `pushEach(field, values)` | `$push $each` | Append multiple values |
| `pushEach(field, values, opts)` | `$push $each` | Append with position/slice/sort options |
| `addToSet(field, value)` | `$addToSet` | Add value to array if not already present |
| `addEachToSet(field, values)` | `$addToSet $each` | Add multiple values to set |
| `pull(field, value)` | `$pull` | Remove all occurrences of a value from array |
| `pullAll(field, values)` | `$pull` | Remove all occurrences of any of the values |
| `pullByFilter(filter)` | `$pull` | Remove array elements matching a filter |
| `popFirst(field)` | `$pop` | Remove the first element of an array |
| `popLast(field)` | `$pop` | Remove the last element of an array |

### Bitwise updates

```scala
Update.bitwiseAnd("flags", 0x0F)
Update.bitwiseOr("flags", 0x01)
Update.bitwiseXor("flags", 0xFF)
```

## Chaining multiple updates

Multiple update operations are combined automatically:

```scala
val update = Update
  .set("status", "reviewed")
  .inc("viewCount", 1)
  .currentDate("lastSeen")
  .push("tags", "featured")

collection.updateOne(Filter.eq("_id", docId), update)
```

You can also merge two `Update` values:

```scala
val base     = Update.set("status", "active")
val extra    = Update.inc("loginCount", 1)
val combined = base.combinedWith(extra)
```

## replaceOne

Replace an entire document (except `_id`) with a new document:

```scala
val newDoc = Document("name" := "Alice", "status" := "active")
collection.replaceOne(Filter.eq("_id", docId), newDoc)

// With upsert
collection.replaceOne(
  Filter.eq("name", "Alice"),
  newDoc,
  ReplaceOptions().upsert(true)
)
```

## Pipeline-based updates (MongoDB 4.2+)

For complex updates that reference the document's own fields, you can use an aggregation pipeline as the update specification by passing a `Seq[Bson]` directly:

```scala
import org.bson.conversions.Bson
import com.mongodb.client.model.Aggregates

val pipeline: Seq[Bson] = Seq(
  Aggregates.set("fullName", Document("$concat" := List("$firstName", " ", "$lastName")))
)
collection.updateMany(Filter.empty, pipeline)
```

## Inspecting UpdateResult

```scala
collection.updateOne(filter, update).flatMap { result =>
  IO.println(s"Matched: ${result.getMatchedCount}, Modified: ${result.getModifiedCount}")
}
```

## Using with a client session (transactions)

```scala
client.startSession.use { session =>
  session.startTransaction *>
    collection.updateMany(session, Filter.eq("status", "pending"), Update.set("status", "processed")) *>
    session.commitTransaction
}
```