---
id: operations
title: Operations
---

Operations listed in this section describe the procedures that can be executed on a `MongoCollection[F, T]` instance. All operations return values wrapped in the effect type `F[_]` (typically `IO` for Cats Effect or `Task` for ZIO). Long-running queries can alternatively be consumed as a stream.

- *[Indexes](operations/indexes)* — Create and manage indexes for efficient querying
- *[Find](operations/find)* — Query documents with filters, sorting, pagination, and projections
- *[Update](operations/update)* — Modify existing documents with a rich set of update operators
- *[Distinct](operations/distinct)* — Retrieve unique field values across a collection
- *[Aggregate](operations/aggregate)* — Build multi-stage aggregation pipelines for complex data transformations
- *[Watch](operations/watch)* — Subscribe to real-time change streams
- *[Transactions](operations/transactions)* — Run multiple operations atomically with ACID guarantees
- *[Bulk Writes](operations/bulk)* — Execute mixed batches of inserts, updates, and deletes efficiently