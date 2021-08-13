---
layout: docs
title:  "Find"
number: 3
---

## Find

Find operation can be used for retrieving a subset of the existing data from a MongoDB collection 
with the option for specifying what data to return, the number of documents to return and in what order. 

The result of an operation can be returned in the following forms:
* The first document that matches a query - `F[Option[T]]`
* All documents bundled in a single collection - `F[Iterable[T]]`
* A Stream of documents where each item is emitted as soon as it is obtained - `fs2.Stream[F, T]`

Find operation can be executed by calling `find` method on a `MongoCollection[F, T]` instance:
```scala
import mongo4cats.bson.Document

val data: fs2.Stream[IO, Document] = collection.find.stream
```
To specify what data to return, additional filters can be passed in:

```scala
import mongo4cats.collection.operations.Filter

val filter1 = Filter.eq("field1", "foo")
val filter2 = Filter.eq("field2", "bar")
val filter3 = Filter.exists("field3")
val data: IO[Option[Document]] = collection.find((filter1 || filter2) && filter2).first
```
As can be noted from the example above, filters are composable and can be chained together using logical operators `||` and `&&`.
The full list of available filters can be found either by exploring API of the `mongo4cats.database.operations.Filter` companion object or by vising the official MongoDB [documentation](https://docs.mongodb.com/drivers/java/sync/current/fundamentals/builders/filters/).

To reduce the number of returned document, `skip` and `limit` methods can be applied:
```scala
val data = IO[Iterable[Document]] = collection.find
  .skip(10) // skip the first 10 
  .limit(100) // take the next 100
  .all
```

The ordering of the data can be enforced by calling either `sortBy` or `sort` method:

```scala
import mongo4cats.collection.operations.Sort

// sort in ascending order by field1, then in descending order by field2
val data = IO[Iterable[Document]] = collection.find.sort(Sort.asc("field1").desc("field2")).all

// same as the above but without Sort specification
val data = IO[Iterable[Document]] = collection.find.sortBy("field1").sortByDesc("field2").all
```