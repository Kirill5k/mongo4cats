---
layout: docs
title:  "Index"
number: 2
---

## Index

Indexes support efficient execution of queries in MongoDB as well as allow efficient sorting, some additional capabilities like unique constraints and geospatial search, and [more](https://docs.mongodb.com/manual/indexes/). 

`MongoCollectionF[T]` supports several ways of creating an index on a field (or multiple fields).
The simplest one would be calling `createIndex` method and passing defined index specification object:
```scala
import mongo4cats.database.operations.Index

val result: IO[String] = collection.createIndex[IO](Index.ascending("field"))
```
To create a compound index, multiple specifications can be combined together:
```scala
import mongo4cats.database.operations.Index

val compoundIndex = Index.ascending("field1").descending("field2")

// or by just combining 2 indexes together
val index1 = Index.ascending("field1")
val index2 = Index.descending("field2")
val compoundIndex = index1.combinedWith(index2)
```
If some additional configuration required, `createIndex` has an overloaded variant which accepts options object:
```scala
import mongo4cats.database.operations.Index
import mongo4cats.database.IndexOptions

val index = Index.ascending("name", "email")
val options = IndexOptions().unique(true)
val result: IO[String] = collection.createIndex[IO](index, options)
```
Alternatively, indexes can be creating by using [builders](https://docs.mongodb.com/drivers/java/sync/current/fundamentals/builders/indexes/) from the standard MongoDB Java library:
```scala
import com.mongodb.client.model.Indexes

val index = Indexes.compoundIndex(Indexes.ascending("field1"), Indexes.ascending("field2"))
val result: IO[String] = collection.createIndex[IO](index)
```