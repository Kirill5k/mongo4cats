---
id: bulk
title: "Bulk Writes"
tags: ["bulk", "bulkWrite", "WriteCommand"]
---

Bulk write operations let you send multiple insert, update, replace, and delete commands to MongoDB in a single network round trip. This is significantly more efficient than executing each operation individually when you need to modify many documents at once.

## WriteCommand types

The `WriteCommand` sealed trait has the following variants:

| Command | Description |
|---|---|
| `WriteCommand.InsertOne(document)` | Insert a single document |
| `WriteCommand.UpdateOne(filter, update)` | Update the first matching document |
| `WriteCommand.UpdateMany(filter, update)` | Update all matching documents |
| `WriteCommand.ReplaceOne(filter, replacement)` | Replace the first matching document |
| `WriteCommand.DeleteOne(filter)` | Delete the first matching document |
| `WriteCommand.DeleteMany(filter)` | Delete all matching documents |

All variants accept an optional options argument for upsert, collation, etc.

## Basic example

```scala
import cats.effect.IO
import mongo4cats.bson.Document
import mongo4cats.bson.syntax._
import mongo4cats.client.MongoClient
import mongo4cats.models.collection.{BulkWriteOptions, WriteCommand}
import mongo4cats.operations.{Filter, Update}

MongoClient.fromConnectionString[IO]("mongodb://localhost:27017").use { client =>
  for {
    db   <- client.getDatabase("mydb")
    coll <- db.getCollection("products")

    // Seed some data first
    _ <- coll.insertMany((1 to 10).map(i => Document("name" := s"item-$i", "qty" := i * 10)))

    commands = List(
      WriteCommand.InsertOne(Document("name" := "item-11", "qty" := 110)),
      WriteCommand.UpdateOne(Filter.eq("name", "item-1"), Update.set("qty", 999)),
      WriteCommand.DeleteOne(Filter.eq("name", "item-2")),
      WriteCommand.DeleteMany(Filter.gt("qty", 50) && Filter.lt("qty", 80)),
      WriteCommand.ReplaceOne(Filter.eq("name", "item-10"), Document("name" := "item-10", "qty" := 0))
    )

    result <- coll.bulkWrite(commands)
    _ <- IO.println(s"Inserted: ${result.getInsertedCount}")
    _ <- IO.println(s"Modified: ${result.getModifiedCount}")
    _ <- IO.println(s"Deleted:  ${result.getDeletedCount}")
  } yield ()
}
```

## Bulk write options

```scala
import mongo4cats.models.collection.BulkWriteOptions

// Ordered (default) — stop on first error
coll.bulkWrite(commands, BulkWriteOptions(ordered = true))

// Unordered — continue processing even if some commands fail
coll.bulkWrite(commands, BulkWriteOptions(ordered = false))
```

With **ordered = true** (the default), MongoDB executes commands in sequence and stops at the first error. With **ordered = false**, MongoDB may execute commands in parallel and reports all errors at the end; this is generally faster for independent writes.

## Upsert on UpdateOne / UpdateMany

```scala
import mongo4cats.models.collection.{UpdateOptions, WriteCommand}
import mongo4cats.operations.{Filter, Update}

val commands = List(
  WriteCommand.UpdateOne(
    Filter.eq("name", "new-item"),
    Update.set("qty", 0),
    UpdateOptions().upsert(true)   // insert if not found
  )
)

coll.bulkWrite(commands)
```

## Inspecting BulkWriteResult

```scala
import com.mongodb.bulk.BulkWriteResult

coll.bulkWrite(commands).map { result =>
  println(s"Acknowledged:   ${result.wasAcknowledged()}")
  println(s"Inserted count: ${result.getInsertedCount}")
  println(s"Matched count:  ${result.getMatchedCount}")
  println(s"Modified count: ${result.getModifiedCount}")
  println(s"Deleted count:  ${result.getDeletedCount}")
  println(s"Upserted count: ${result.getUpserts.size}")
}
```

## Using with a client session (transactions)

```scala
client.startSession.use { session =>
  for {
    _ <- session.startTransaction
    _ <- coll.bulkWrite(session, commands, BulkWriteOptions(ordered = true))
    _ <- session.commitTransaction
  } yield ()
}
```
