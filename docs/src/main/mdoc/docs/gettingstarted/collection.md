---
layout: docs
title:  "Getting a collection"
number: 3
---

## Getting a collection

An instance of `MongoCollectionF` can be obtained from an existing database instance by specifying collection's name:

```scala
import mongo4cats.bson.Document
import mongo4cats.database.MongoCollectionF

val collection: IO[MongoCollectionF[Document]] = database.getCollection("mycoll")
```

Alternatively, if collection needs to be tied to a specific class, `MongoDatabaseF` has special methods for doing this as well:

```scala
// needs to have an instance of CodecRegistry built for the provided class
val collection: IO[MongoCollectionF[MyClass]] = database.getCollection[MyClass]("mycoll", myClassCodecRegistry)

// needs to have an instance of MongoCodecProvider[MyClass] available in the implicit scope
val collection: IO[MongoCollectionF[MyClass]] = database.getCollectionWithCodec[MyClass]("mycoll")
```

One of the supported options for deriving MongoDB codecs is through the use of the popular JSON library for Scala - [Circe](../circe.html).
More information on MongoDB codecs and codec registries can be found in the [official documentation](http://mongodb.github.io/mongo-java-driver/4.3/bson/codecs/)

If a collection that you are trying to obtain does not exist, it will be created by MongoDB during the first query. Additionally, `MongoDatabaseF` has methods for creating collections explicitly:
```scala
database.createCollection("mycoll")

// or with options
import mongo4cats.database._

database.createCollection("my coll", CreateCollectionOptions().capped(true).sizeInBytes(1024L))
```
