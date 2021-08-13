---
layout: docs
title:  "Updates"
number: 4
---

## Update

Update operations allow modifying fields and values of a single or multiple documents.
When executed, the update operation will apply changes specified in an update query to all documents that match a filter query.
`MongoCollectionF[T]` has several methods for submitting an update query: `updateOne` updates the first document that matches a filter, whereas `updateMany` will update all documents.
```scala
import mongo4cats.database.operations.{Filter, Update}
import mongo4cats.database.UpdateOptions

val update = Update.set("field1", "foo").currentDate("date")

val result: IO[UpdateResult] = collection.updateOne[IO](Filter.empty, update)
// or with options
val result: IO[UpdateResult] = collection.updateOne[IO](Filter.empty, update, UpdateOptions().upsert(true))
```
As an alternative, an update query can be built using [builder](https://docs.mongodb.com/drivers/java/sync/current/fundamentals/builders/updates/) from the standard MongoDB library:
```scala
import com.mongodb.client.model.{Filters, Updates}

val update = Updates.combine(Updates.set("field1", "foo"), Updates.currentDate("date"))
val result: IO[UpdateResult] = collection.updateOne[IO](Filters.empty(), update)
```