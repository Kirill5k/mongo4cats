---
id: bulk
title: "Bulk Writes"
tags: ["bulk", "bulkWrite", "WriteCommand", "ClientWriteCommand"]
---

Bulk write operations combine insert, update, replace, and delete commands in one API call. The driver batches commands according to server limits, so one call can require multiple network round trips.

Use `collection.bulkWrite` for writes to one collection. Use `client.bulkWrite` for writes spanning collections or databases on the same MongoDB deployment; client-level bulk writes require **MongoDB 8.0 or later**.

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

Update, replace, and delete variants accept an optional options argument for upsert, collation, etc. `InsertOne` takes only its document.

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

## Client-level bulk writes

`MongoClient` and `ZMongoClient` accept a sequence of `ClientWriteCommand` values. Every command includes a `MongoNamespace(databaseName, collectionName)`. Insert and replacement commands can contain different document types in the same sequence, provided the client has codecs for each type.

```scala
import cats.effect.IO
import mongo4cats.bson.Document
import mongo4cats.bson.syntax._
import mongo4cats.client.MongoClient
import mongo4cats.models.client.{ClientBulkWriteOptions, ClientWriteCommand}
import mongo4cats.models.collection.MongoNamespace
import mongo4cats.operations.{Filter, Update}

val products = MongoNamespace("shop", "products")
val events = MongoNamespace("audit", "events")

val commands: List[ClientWriteCommand] = List(
  ClientWriteCommand.InsertOne(products, Document("sku" := "widget", "qty" := 10)),
  ClientWriteCommand.UpdateOne(products, Filter.eq("sku", "widget"), Update.inc("qty", 5)),
  ClientWriteCommand.InsertOne(events, Document("action" := "stock-added", "sku" := "widget"))
)

MongoClient.fromConnectionString[IO]("mongodb://localhost:27017").use { client =>
  client.bulkWrite(commands, ClientBulkWriteOptions(verboseResults = true)).flatMap { result =>
    IO.println(s"Inserted: ${result.getInsertedCount}, modified: ${result.getModifiedCount}")
  }
}
```

The default ordered execution ensures that this example inserts the product before updating it. A bulk write is **not atomic across operations**: earlier successful writes are not rolled back if a later write fails. Passing a session alone does not start a transaction.

### Commands and options

The client commands mirror the collection commands, with the namespace as the first argument:

| Command | Arguments after the namespace |
|---|---|
| `ClientWriteCommand.InsertOne` | `document` |
| `ClientWriteCommand.UpdateOne` / `UpdateMany` | `filter`, `update`, optional client update options |
| `ClientWriteCommand.PipelinedUpdateOne` / `PipelinedUpdateMany` | `filter`, pipeline `Seq[Bson]`, optional client update options |
| `ClientWriteCommand.ReplaceOne` | `filter`, `replacement`, optional client replace options |
| `ClientWriteCommand.DeleteOne` / `DeleteMany` | `filter`, optional client delete options |

Use the client-specific operation options from `mongo4cats.models.client`: `ClientUpdateOneOptions`, `ClientUpdateManyOptions`, `ClientReplaceOneOptions`, `ClientDeleteOneOptions`, and `ClientDeleteManyOptions`. Collection option types are not interchangeable with them. For example:

```scala
import mongo4cats.models.client.ClientUpdateOneOptions

ClientWriteCommand.UpdateOne(
  products,
  Filter.eq("sku", "new-widget"),
  Update.set("qty", 10),
  ClientUpdateOneOptions(upsert = true)
)
```

`ClientBulkWriteOptions` controls the whole request:

| Option | Default | Meaning |
|---|---|---|
| `ordered` | `true` | Stop after the first individual write error. When false, individual write errors do not prevent other operations from executing. |
| `verboseResults` | `false` | Include results for individual successful operations. |
| `bypassDocumentValidation` | `false` | Bypass collection document validation rules. |
| `comment` | `None` | Attach an optional string comment to the command. |
| `let` | `None` | Supply command-level variables as an optional BSON document. |

Top-level failures, such as connection errors, can stop execution even when `ordered = false`. The driver handles batching and retry eligibility; avoid blindly retrying an entire failed bulk because some operations may already have succeeded.

Both effect implementations provide these four overloads:

```scala
client.bulkWrite(commands)
client.bulkWrite(commands, ClientBulkWriteOptions(ordered = false))
client.bulkWrite(session, commands)
client.bulkWrite(session, commands, ClientBulkWriteOptions(verboseResults = true))
```

The result is `F[com.mongodb.client.model.bulk.ClientBulkWriteResult]` for Cats Effect and `Task[ClientBulkWriteResult]` for ZIO. Sessions must belong to the same client. Use a replica set or sharded deployment for sessions and transactions; the embedded helpers start standalone servers.

Custom subclasses or test doubles of `GenericMongoClient` must implement the two new overloads that take explicit `ClientBulkWriteOptions`; the default-options overloads delegate to them.

### Results and partial failures

For an acknowledged result, `getInsertedCount`, `getUpsertedCount`, `getMatchedCount`, `getModifiedCount`, and `getDeletedCount` report totals across namespaces. Check `isAcknowledged` before accessing details if you configure unacknowledged write concern. Unacknowledged writes (`w=0`) require both `ordered = false` and `verboseResults = false`; the driver rejects other combinations.

`getVerboseResults` returns a Java `Optional`. With `verboseResults = true`, its insert, update, and delete result maps identify individual operations by their **zero-based index in the original command sequence**, including when the driver sends multiple batches. Map iteration order is unspecified.

Errors remain in the effect's error channel. The original `com.mongodb.ClientBulkWriteException` exposes:

- `getWriteErrors`: individual write errors keyed by the original command index.
- `getWriteConcernErrors`: write concern failures, which are separate from individual write errors.
- `getPartialResult`: an optional result for operations known to have succeeded. It is present only when the driver received a response indicating success for at least one operation.
- `getCause`: the top-level failure, or `null` when none occurred. Use its error code and labels when inspecting a top-level failure.

Some failures have no partial result, and failures without any write-error, write-concern-error, or partial-result information can surface as another `MongoException`. An absent partial result does not establish that no writes occurred.

For example, log the available information while keeping the failure visible to the caller:

```scala
import com.mongodb.ClientBulkWriteException

client.bulkWrite(commands, ClientBulkWriteOptions(ordered = false)).attempt.flatMap {
  case Right(result) => IO.println(s"Inserted: ${result.getInsertedCount}")
  case Left(error: ClientBulkWriteException) =>
    val partial = error.getPartialResult
    val inserted = if (partial.isPresent) Some(partial.get().getInsertedCount) else None
    IO.println(
      s"Write errors: ${error.getWriteErrors}; write concern errors: ${error.getWriteConcernErrors}; known inserted: $inserted"
    ).flatMap(_ => IO.raiseError[Unit](error))
  case Left(error) => IO.raiseError[Unit](error)
}
```

### Codecs for typed documents

Client bulk writes use the **client's codec registry**. The mongo4cats client settings builder includes the library's default codecs, including `mongo4cats.bson.Document`. Codecs added only to a database or collection do not apply to client bulk writes.

For custom document types, merge their codec providers into the client registry before creating the client. For example, with the Circe module:

```scala
import cats.effect.IO
import io.circe.generic.auto._
import mongo4cats.circe._
import mongo4cats.client.MongoClient
import mongo4cats.codecs.CodecRegistry
import mongo4cats.models.client.{ConnectionString, MongoClientSettings}

case class Product(sku: String, qty: Int)

val provider = deriveCirceCodecProvider[Product]
val registry = CodecRegistry.mergeWithDefault(CodecRegistry.from(provider.get))
val settings = MongoClientSettings
  .builder(codecRegistry = registry)
  .applyConnectionString(ConnectionString("mongodb://localhost:27017"))
  .build()

val clientResource = MongoClient.create[IO](settings)
```

The same registry configuration works with `ZMongoClient.create(settings)`.
