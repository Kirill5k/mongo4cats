---
layout: docs
title:  "Find"
number: 3
---

## Find

The Find operation can be used to retrieve a subset of the existing data from a MongoDB collection 
with the option for specifying what data to return, the number of documents to return and in what order. 

The result of an operation can be returned in the following forms:
* The first document that matches a query - `F[Option[T]]`
* All documents bundled in a single collection - `F[Iterable[T]]`
* A Stream of documents where each item is emitted as soon as it is obtained - `fs2.Stream[F, T]`

Find operation can be executed by calling `find` method on a `MongoCollection[T]` instance:
```scala
val data: fs2.Stream[IO, Document] = collection.find.stream[IO]
```
To specify what data to return, additional filters can be passed in:
```scala
import mongo4cats.database.operations.Filter

val filter1 = Filter.eq("field1", "foo")
val filter2 = Filter.eq("field2", "bar")

val data: IO[Option[Document]] = collection.find(filter1 || filter2).first[IO]
```
As can be noted from the example above, filters are composable and can be chained together using logical operators `||` and `&&`.
The full list of available filters can be found either by exploring API of the `mongo4cats.database.operations.Filter` companion object or by vising the official MongoDB [documentation](https://docs.mongodb.com/drivers/java/sync/current/fundamentals/builders/filters/).

To reduce the number of returned document, `skip` and `limit` methods can be applied:
```scala
val data = IO[Iterable[Document]] = collection
  .find
  .skip(10) // skip the first 10 
  .limit(100) // take the next 100
  .all[IO]
```

The ordering of the data can be set by either calling a `sortBy` method or `sort`:
```scala
import mongo4cats.database.operations.Sort

// sort in ascending order by field1, then in descending order by field2
val data = IO[Iterable[Document]] = collection.find.sort(Sort.asc("field1").desc("field2")).all[IO]

// the same as the above
val data = IO[Iterable[Document]] = collection.find.sortBy("field1").sortByDesc("field2").all[IO]
```