---
layout: docs
title:  "Aggregate"
number: 6
---

## Aggregate

The Aggregation operations can be used for processing data from multiple MongoDB collections and returning combined results.
In MongoDB aggregations are represented in a form of data processing pipelines where documents go through multiple transformations defined in each step.
More detailed explanation of the aggregation process can be found in the official [documentation](https://docs.mongodb.com/drivers/java/sync/current/fundamentals/aggregation/).

To create such aggregation pipeline, `Aggregate` constructor can be used:
```scala
import mongo4cats.database.operations.{Aggregate, Accumulator, Sort}

// specification for grouping multiple transactions from the same group:
val accumulator = Accumulator
  .sum("count", 1) // number of transactions in a given group
  .sum("totalAmount", "$amount") // total amount
  .first("categoryId", "$category._id") // id of a category under which all transactions are grouped

val aggregation = Aggregate
  .group("$category", accumulator) // group all transactions by categoryId and accumulate result into a given specification
  .lookup("categories", "categoryId", "_id", "category") // find a category for each group of transactions by category id
  .sort(Sort.desc("totalAmount")) // define the order of the produced results
```
Once the aggregation pipeline is defined, the aggregation operation can be executed by calling `aggregate` method on a `MongoCollectionF[F]` instance. 
Similarly to `find`, the result of `aggregate` can be returned in a form of a single document, list of documents or a stream:
```scala
val result: IO[Option[Document]] = collection.aggregate[Document](aggregation).first[IO]
val result: IO[Iterable[Document]] = collection.aggregate[Document](aggregation).all[IO]
val result: fs2.Stream[IO, Document] = collection.aggregate[Document](aggregation).stream[IO]
```
Analogously to `distinct`, the result of an aggregation can be tied to a specific class:
```scala
val result: fs2.Stream[IO, MyClass] = collection.aggregateWithCodec[MyClass](aggregation).stream[IO]
```
If aggregation pipeline ends with the `$out` stage (write document to a specified collection), `toCollection` method can be used:
```scala
val result: IO[Unit] = collection.aggregate[Document](aggregation).toCollection[IO]
```