---
id: collection
title: "Getting a collection"
tags: ["MongoCollection"]
---

## MongoCollection

`MongoCollection[F, T]` is the main abstraction for interacting with a MongoDB collection. The type parameter `T` is the document type — by default `Document`, but it can be any type that has a registered BSON codec.

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

## Available operations

Once you have a collection, the following operations are available:

| Category | Methods |
|---|---|
| **Insert** | `insertOne`, `insertMany` |
| **Find** | `find`, `findOneAndDelete`, `findOneAndUpdate`, `findOneAndReplace` |
| **Update** | `updateOne`, `updateMany`, `replaceOne` |
| **Delete** | `deleteOne`, `deleteMany` |
| **Count** | `count` |
| **Aggregate** | `aggregate`, `aggregateWithCodec` |
| **Distinct** | `distinct`, `distinctWithCodec` |
| **Indexes** | `createIndex`, `listIndexes`, `dropIndex`, `dropIndexes` |
| **Bulk** | `bulkWrite` |
| **Watch** | `watch` |
| **Admin** | `drop`, `renameCollection` |

All methods are described in the [Operations](../operations) section.
