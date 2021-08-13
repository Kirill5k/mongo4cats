---
layout: docs
title:  "Getting a collection"
number: 3
---

## Getting a collection

An instance of `MongoCollection[T]` can be obtained from an existing database instance by specifying collection's name:

```scala
import mongo4cats.bson.Document
import mongo4cats.collection.MongoCollection

val collection: IO[MongoCollection[Document]] = database.getCollection("mycoll")
```

Alternatively, if collection needs to be tied to a specific class, `MongoDatabase[F]` has special methods for doing this as well:

```scala
// needs to have an instance of CodecRegistry built for the provided class
val collection: IO[MongoCollection[MyClass]] = database.getCollection[MyClass]("mycoll", myClassCodecRegistry)

// needs to have an instance of MongoCodecProvider[MyClass] available in the implicit scope
val collection: IO[MongoCollection[MyClass]] = database.getCollectionWithCodec[MyClass]("mycoll")
```
More information on MongoDB codecs and codec registries can be found in the [official documentation](https://docs.mongodb.com/drivers/java/sync/current/fundamentals/data-formats/codecs/).
One of the supported options for deriving MongoDB codecs is through the use of the popular Json library for Scala - [Circe](../circe.html).

If a collection that you are trying to obtain does not exist, it will be created by MongoDB during the first query. Additionally, `MongoDatabase[F]` has methods for creating collections explicitly:

```scala
val collection: IO[Unit] = database.createCollection("mycoll")

// or with options

import mongo4cats.collection.CreateCollectionOptions

val options = CreateCollectionOptions().capped(true).sizeInBytes(1024L)
val collection: IO[Unit] = database.createCollection("my coll", options)
```
