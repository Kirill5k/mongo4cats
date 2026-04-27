---
id: distinct
title: "Distinct"
tags: ["distinct"]
---

The distinct operation returns all unique values for a specified field across a collection, equivalent to SQL's `SELECT DISTINCT`. It is useful for building autocomplete lists, enumerating categories, or checking what values actually exist in production data.

## Basic usage

```scala
import cats.effect.IO

// Collect all distinct string values
val statuses: IO[Iterable[String]] = collection.distinct[String]("status").all

// Stream distinct values one at a time
val stream: fs2.Stream[IO, String] = collection.distinct[String]("status").stream
```

## With a filter

Pass a `Filter` to restrict the documents considered before computing distinct values:

```scala
import mongo4cats.operations.Filter

// Only look at active documents
val activeCategories: IO[Iterable[String]] =
  collection.distinct[String]("category", Filter.eq("active", true)).all
```

## Complex value types

If the field holds a document or a type other than a primitive, use the appropriate type parameter:

```scala
import mongo4cats.bson.Document

// Field holds an embedded document
val distinctDocs: IO[Iterable[Document]] = collection.distinct[Document]("address").all
```

For a custom case class, provide a `MongoCodecProvider[MyClass]` in the implicit scope (e.g. via Circe derivation) and use `distinctWithCodec`:

```scala
import io.circe.generic.auto._
import mongo4cats.circe._

final case class Tag(name: String, weight: Double)

val distinctTags: IO[Iterable[Tag]] =
  collection.distinctWithCodec[Tag]("tags").all
```

Alternatively, add codec support to the collection on the fly:

```scala
val distinctTags: IO[Iterable[Tag]] =
  collection.withAddedCodec(myTagCodecRegistry).distinct[Tag]("tags").all
```

## Using with a client session (transactions)

```scala
client.startSession.use { session =>
  collection.distinct[String](session, "status", Filter.empty).all
}
```