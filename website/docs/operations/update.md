---
id: update
title:  "Update"
tags: ["Update"]
---

Update operations allow modifying fields and values of a single or multiple documents.
When executed, the update operation will apply changes specified in an update query to all documents that match a filter query.
`MongoCollection[F, T]` has several methods for submitting an update query: `updateOne` updates the first document that matches a filter, whereas `updateMany` will update all documents.

```scala
import mongo4cats.operations.{Filter, Update}
import mongo4cats.models.collection.UpdateOptions

// chain multiple updates together
val update = Update.set("field1", "foo").currentDate("date")

val result: IO[UpdateResult] = collection.updateOne(Filter.empty, update)
// or with options
val result: IO[UpdateResult] = collection.updateOne(Filter.empty, update, UpdateOptions().upsert(true))
```
As an alternative, an update query can be built using [builder](https://docs.mongodb.com/drivers/java/sync/current/fundamentals/builders/updates/) from the standard MongoDB library:
```scala
import com.mongodb.client.model.{Filters, Updates}

val update = Updates.combine(Updates.set("field1", "foo"), Updates.currentDate("date"))
val result: IO[UpdateResult] = collection.updateOne(Filters.empty(), update)
```