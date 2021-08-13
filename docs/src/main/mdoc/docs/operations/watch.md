---
layout: docs
title:  "Watch"
number: 7
---

## Watch

Watch operation allows monitoring for changes in a single collection. The change stream can be created by calling `watch` method on a `MongoCollection[F, T]` instance, 
which can also optionally take an aggregation pipeline as an argument. Once created, the change stream will start emitting [change event](https://docs.mongodb.com/manual/reference/change-events/) documents whenever changes are being produced.

```scala
import mongo4cats.bson.Document

val changes: fs2.Stream[IO, Document] = collection.watch[Document].stream

// or with an aggregation pipeline included
import mongo4cats.collection.operations.{Aggregate, Filter}

val changes: fs2.Stream[IO, Document] = collection.watch[Document](Aggregate.matchBy(Filter.gte("amount", 100))).stream
```