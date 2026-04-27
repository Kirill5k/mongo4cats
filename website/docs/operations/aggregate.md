---
id: aggregate
title: "Aggregate"
tags: ["Aggregate", "Accumulator", "pipeline"]
---

Aggregation pipelines process documents through a sequence of stages, where the output of each stage is the input to the next. This is MongoDB's most powerful query mechanism — it supports grouping, joining collections, computing new fields, bucketing, full-text search, vector search, and more.

Full background is in the [official MongoDB documentation](https://docs.mongodb.com/manual/aggregation/).

## Building a pipeline

Pipeline stages are added by chaining methods on the `Aggregate` object:

```scala
import mongo4cats.operations.{Accumulator, Aggregate, Filter, Projection, Sort}

val pipeline = Aggregate
  .matchBy(Filter.gte("amount", 100))          // $match — filter early to reduce work
  .group("$category", Accumulator               // $group — group by category
    .sum("total", "$amount")
    .count("count")
    .first("categoryName", "$category.name"))
  .sort(Sort.desc("total"))                     // $sort — order results
  .limit(10)                                    // $limit — top 10 categories
```

## Executing the pipeline

```scala
import mongo4cats.bson.Document

// Get the first result
val first: IO[Option[Document]] = collection.aggregate[Document](pipeline).first

// Collect all results
val all: IO[Iterable[Document]] = collection.aggregate[Document](pipeline).all

// Stream results
val stream: fs2.Stream[IO, Document] = collection.aggregate[Document](pipeline).stream
```

### With a typed result

If the pipeline output maps to a Scala case class, use `aggregateWithCodec`:

```scala
import io.circe.generic.auto._
import mongo4cats.circe._

final case class CategorySummary(category: String, total: Double, count: Int)

val result: fs2.Stream[IO, CategorySummary] =
  collection.aggregateWithCodec[CategorySummary](pipeline).stream
```

## Stage reference

### Filtering and shaping

| Stage | Method | Description |
|---|---|---|
| `$match` | `.matchBy(filter)` | Filter documents |
| `$project` | `.project(projection)` | Include/exclude/compute fields |
| `$addFields` / `$set` | `.addFields(...)` / `.set(...)` | Add new computed fields |
| `$unset` | `.unset(fields*)` | Remove fields |
| `$replaceWith` | `.replaceWith(expr)` | Replace document with an expression |

### Grouping and counting

| Stage | Method | Description |
|---|---|---|
| `$group` | `.group(id, accumulator)` | Group documents and accumulate values |
| `$count` | `.count` / `.count(field)` | Count documents into a field |
| `$sortByCount` | `.sortByCount(expr)` | Group by an expression and sort by count |
| `$bucket` | `.bucket(groupBy, boundaries)` | Categorise into fixed buckets |
| `$bucketAuto` | `.bucketAuto(groupBy, n)` | Automatically determine n buckets |
| `$facet` | `.facet(facets*)` | Run multiple sub-pipelines in parallel |

### Pagination

| Stage | Method | Description |
|---|---|---|
| `$sort` | `.sort(sort)` | Sort documents |
| `$skip` | `.skip(n)` | Skip the first n documents |
| `$limit` | `.limit(n)` | Limit to n documents |
| `$sample` | `.sample(n)` | Randomly sample n documents |

### Joining collections

```scala
// Simple equality join — adds a "category" array field
Aggregate.lookup("categories", "categoryId", "_id", "category")

// Join with a sub-pipeline (e.g. with additional filtering)
val subPipeline = Aggregate.matchBy(Filter.eq("active", true))
Aggregate.lookup("categories", subPipeline, "category")
```

`$graphLookup` for recursive lookups (e.g. org charts, friend-of-friend):

```scala
Aggregate.graphLookup(
  from           = "employees",
  startWith      = "$reportsTo",
  connectFromField = "reportsTo",
  connectToField = "_id",
  as             = "reportingHierarchy"
)
```

### Unwinding arrays

`$unwind` deconstructs an array field so that each element becomes its own document:

```scala
// Emit one document per tag
Aggregate.unwind("$tags")

// Preserve documents where the array is missing or empty
import mongo4cats.models.collection.UnwindOptions
Aggregate.unwind("$tags", UnwindOptions().preserveNullAndEmptyArrays(true))
```

### Writing results

```scala
// Write results to a collection (same database)
Aggregate.out("outputCollection")

// Write to a different database
Aggregate.out("otherDb", "outputCollection")

// Merge results into a collection (upsert semantics)
Aggregate.merge("targetCollection")

collection.aggregate[Document](pipeline).toCollection
```

### Set window fields

Compute running totals, moving averages, and other window functions:

```scala
import com.mongodb.client.model.{WindowOutputField, Windows}

Aggregate.setWindowFields(
  partitionBy = "$category",
  sortBy      = Sort.asc("date"),
  outputs     = Seq(
    WindowOutputField.sum("runningTotal", "$amount", Windows.unboundedPreceding())
  )
)
```

### Union

Combine documents from two collections:

```scala
val otherPipeline = Aggregate.matchBy(Filter.eq("source", "external"))
Aggregate.unionWith("otherCollection", otherPipeline)
```

### Densify and fill (MongoDB 5.1+)

```scala
import com.mongodb.client.model.densify.{DensifyOptions, DensifyRange}
import com.mongodb.client.model.fill.{FillOptions, FillOutputField}

// Fill in missing date steps
Aggregate.densify("date", DensifyRange.fullRangeWithStep(DensifyOptions.densifyOptions(), 1, "day"))

// Replace null/missing values
Aggregate.fill(
  FillOptions.fillOptions().sortBy(Sort.asc("date").toBson),
  FillOutputField.locf("price")  // last observation carried forward
)
```

## Accumulator reference

`Accumulator` is used with `.group(id, accumulator)` to compute aggregate values per group:

```scala
val acc = Accumulator
  .sum("totalAmount", "$amount")      // $sum
  .count("count")                     // $sum: 1
  .avg("avgAmount", "$amount")        // $avg
  .first("firstItem", "$name")        // $first
  .last("lastItem", "$name")          // $last
  .min("minAmount", "$amount")        // $min
  .max("maxAmount", "$amount")        // $max
  .push("items", "$name")             // $push — collect into array
  .addToSet("uniqueItems", "$name")   // $addToSet — collect unique values
```

## Combining pipelines

Two `Aggregate` values can be concatenated:

```scala
val stage1 = Aggregate.matchBy(Filter.eq("active", true))
val stage2 = Aggregate.sort(Sort.desc("score")).limit(5)
val combined = stage1.combinedWith(stage2)
```

## Atlas Search and Vector Search

These stages require a MongoDB Atlas cluster with Atlas Search enabled.

```scala
import com.mongodb.client.model.search.{SearchOperator, SearchOptions}

// Full-text Atlas Search
Aggregate.search(
  SearchOperator.text(FieldSearchPath.fieldPath("description"), "functional programming")
)

// Vector search (requires a vector index)
import com.mongodb.client.model.search.{FieldSearchPath, VectorSearchOptions}

Aggregate.vectorSearch(
  path        = FieldSearchPath.fieldPath("embedding"),
  queryVector = queryEmbedding,
  index       = "vector_index",
  limit       = 10L,
  options     = VectorSearchOptions.approximateVectorSearchOptions(100L)
)
```

## Using raw Document expressions

The typed `Aggregate` builders cover the most common stages, but MongoDB's aggregation language has hundreds of operators. Whenever a built-in method doesn't exist for a particular operator (e.g. `$filter`, `$map`, `$reduce`, `$cond`, `$switch`, custom `$expr` expressions), you can drop down to raw `Document` / `BsonValue` values and pass them directly to `addFields`, `set`, `project`, and `group`.

### Conditional expressions with $cond

A common use case is computing a derived field whose value depends on another field. `addFields` accepts any `Document` as the expression value:

```scala
import mongo4cats.bson.{BsonValue, Document}
import mongo4cats.bson.syntax._
import mongo4cats.operations.{Aggregate, Filter}

// Label each order as "high" or "standard" based on its total value
val pipeline = Aggregate
  .matchBy(Filter.eq("status", "completed"))
  .addFields(
    "priority" -> Document(
      "$cond" := Document(
        "if"   := BsonValue.document("$gte" := BsonValue.array(BsonValue.string("$total"), BsonValue.int(500))),
        "then" := BsonValue.string("high"),
        "else" := BsonValue.string("standard")
      )
    )
  )
```

### Filtering arrays inline with $filter

`$filter` keeps only the array elements that match a condition. Pass the expression as a `Document` to `addFields`:

```scala
// Keep only the order lines where quantity > 0
val pipeline = Aggregate
  .matchBy(Filter.eq("status", "pending"))
  .addFields(
    "activeLines" -> Document(
      "$filter" := Document(
        "input" := "$lines",
        "as"    := "line",
        "cond"  := BsonValue.document(
          "$gt" := BsonValue.array(BsonValue.string("$$line.qty"), BsonValue.int(0))
        )
      )
    )
  )
```

### Computed projections from raw expressions

`Projection.computed(fieldName, expression)` accepts any value as the expression, so you can shape the output document using raw operators:

```scala
import mongo4cats.operations.Projection

val pipeline = Aggregate
  .matchBy(Filter.eq("category", "electronics"))
  .project(
    Projection
      .excludeId
      .include("name")
      .computed(
        "discountedPrice",
        Document("$multiply" := BsonValue.array(BsonValue.string("$price"), BsonValue.double(0.9)))
      )
      .computed(
        "inStock",
        Document("$gt" := BsonValue.array(BsonValue.string("$qty"), BsonValue.int(0)))
      )
  )
```

### The general rule

Any place in the `Aggregate` or `Projection` API that accepts a `TExpression` type parameter will accept a `Document` or `BsonValue`. This means the entire MongoDB aggregation expression language is accessible — the typed builders are a convenience layer on top of the same underlying BSON.