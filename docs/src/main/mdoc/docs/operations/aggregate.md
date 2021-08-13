---
layout: docs
title:  "Aggregate"
number: 6
---

## Aggregate

Aggregation operations can be used for processing data from multiple MongoDB collections and returning combined results.
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

val result: IO[Iterable[Document]] = collection.aggregate(aggregation).all[IO]
```