---
id: collection
title: "Getting a collection"
tags: ["MongoCollection"]
---

## MongoCollection

`MongoCollection[F, T]` is the main abstraction for interacting with a MongoDB collection. The type parameter `T` is the document type — by default `Document`, but it can be any type that has a registered BSON codec.

Collection operations returning `F` (or `Task` for ZIO) defer driver invocation until the effect runs. Synchronous driver validation errors and asynchronous operation failures are reported through the effect's error channel. Running the same effect again invokes the driver again; results are not cached. Query builders likewise defer execution until a terminal operation such as `all`, `first`, or `stream` is run.

## Untyped collections (Document)

The simplest way to get a collection works with the generic `Document` type:

```scala
import cats.effect.IO
import mongo4cats.bson.Document
import mongo4cats.collection.MongoCollection

val collection: IO[MongoCollection[IO, Document]] = database.getCollection("mycoll")
```

## Typed collections

If you want to read and write a specific Scala case class instead of raw `Document`s, use one of the typed variants.

### With an explicit codec registry

```scala
import org.bson.codecs.configuration.CodecRegistry

val collection: IO[MongoCollection[IO, MyClass]] =
  database.getCollection[MyClass]("mycoll", myClassCodecRegistry)
```

### With an implicit MongoCodecProvider

This is the most ergonomic option when using [Circe](../circe) or [ZIO JSON](../zio) codec derivation, since both modules provide the `MongoCodecProvider[T]` implicit automatically:

```scala
import io.circe.generic.auto._
import mongo4cats.circe._

// MongoCodecProvider[MyClass] is derived automatically from Circe's Encoder/Decoder
val collection: IO[MongoCollection[IO, MyClass]] =
  database.getCollectionWithCodec[MyClass]("mycoll")
```

Collections inherit the database's codecs, including those added with `withAddedCodec`. A registry supplied to `getCollection` or a provider supplied to `getCollectionWithCodec` takes precedence for the types it supports; other types use the inherited codecs. This applies to both Cats Effect and ZIO, and leaves the database's registry unchanged.

More information on codecs can be found in the [official documentation](https://docs.mongodb.com/drivers/java/sync/current/fundamentals/data-formats/codecs/) and in the [Circe](../circe) section.

## Creating collections explicitly

If a collection does not exist, MongoDB creates it on the first write. You can also create it explicitly in advance:

```scala
database.createCollection("mycoll")
```

With options — for example a [capped collection](https://www.mongodb.com/docs/manual/core/capped-collections/) with a maximum size:

```scala
import mongo4cats.models.database.CreateCollectionOptions

val options = CreateCollectionOptions().capped(true).sizeInBytes(1024L * 1024L)
database.createCollection("mycoll", options)
```

Both forms also accept a session as their first argument: `database.createCollection(session, "mycoll")` and `database.createCollection(session, "mycoll", options)`.

## Collection properties

`MongoCollection[F, T]` exposes several read-only properties:

```scala
val ns: MongoNamespace   = collection.namespace    // database + collection name
val cls: Class[T]        = collection.documentClass
val reg: CodecRegistry   = collection.codecs
```

## Configuring read/write behaviour

You can adjust consistency settings on a per-collection basis without modifying the global client configuration:

```scala
import com.mongodb.{ReadConcern, ReadPreference, WriteConcern}

val strictCollection = collection
  .withReadPreference(ReadPreference.primary())
  .withWriteConcern(WriteConcern.MAJORITY)
  .withReadConcern(ReadConcern.MAJORITY)
```

## Operation timeouts

Databases inherit the client's operation timeout, and collections inherit their database's timeout. Use `withTimeout` to create a wrapper with an override:

```scala
import scala.concurrent.duration._

val timedDatabase = database.withTimeout(5.seconds)
val configuredCollection = timedDatabase.getCollection("mycoll").map { inherited =>
  val timeout: Option[FiniteDuration] = inherited.timeout // Some(5.seconds)
  inherited.withTimeout(2.seconds)
}

val unlimitedCollection = collection.withTimeout(Duration.Zero)
```

`database.timeout` and `collection.timeout` return `None` when no timeout is configured, or `Some(Duration.Zero)` when explicitly unlimited. A configured operation timeout, including zero, takes precedence over `maxTime` and the driver's legacy timeout settings. The driver validates durations. Configuring a timeout preserves codecs and other settings; obtaining typed collections or adding codecs preserves the timeout. The original database and collection wrappers remain unchanged.

## Counting documents

Use `count` for an accurate count, optionally with a filter, and `estimatedDocumentCount` for a metadata-based estimate of the whole collection:

```scala
import mongo4cats.models.collection.EstimatedDocumentCountOptions
import mongo4cats.operations.Filter
import scala.concurrent.duration._

val matching: IO[Long] = collection.count(Filter.eq("status", "active"))
val estimated: IO[Long] = collection.estimatedDocumentCount
val boundedEstimate: IO[Long] = collection.estimatedDocumentCount(
  EstimatedDocumentCountOptions(maxTime = 2.seconds, comment = Some("dashboard total"))
)
```

Estimated counts have no filter or session overload. Use `count(session, filter)` when a count needs to participate in a session.

## Available operations

Once you have a collection, the following operations are available:

| Category | Methods |
|---|---|
| **Insert** | `insertOne`, `insertMany` |
| **Find** | `find`, `findOneAndDelete`, `findOneAndUpdate`, `findOneAndReplace` |
| **Update** | `updateOne`, `updateMany`, `replaceOne` |
| **Delete** | `deleteOne`, `deleteMany` |
| **Count** | `count`, `estimatedDocumentCount` |
| **Aggregate** | `aggregate`, `aggregateWithCodec` |
| **Distinct** | `distinct`, `distinctWithCodec` |
| **Indexes** | `createIndex`, `listIndexes`, `dropIndex`, `dropIndexes` |
| **Search indexes** | `createSearchIndex`, `createSearchIndexes`, `listSearchIndexes`, `updateSearchIndex`, `dropSearchIndex` |
| **Bulk** | `bulkWrite` |
| **Watch** | `watch` |
| **Admin** | `drop`, `renameCollection` |

All methods are described in the [Operations](../operations) section.
