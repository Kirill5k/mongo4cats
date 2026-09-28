---
id: operations
title: Operations
---

This section covers operations on collections, databases, and clients. Most reads and writes use a collection; change streams can watch a collection, a database, or the deployment through a client, and client-level bulk writes can span collections and databases. Results are returned in the effect type `F[_]` (typically `IO` for Cats Effect or `Task` for ZIO), or consumed as streams where supported.

- *[Indexes](operations/indexes)* — Create and manage indexes for efficient querying
- *[Find](operations/find)* — Query documents with filters, sorting, pagination, and projections
- *[Update](operations/update)* — Modify existing documents with a rich set of update operators
- *[Distinct](operations/distinct)* — Retrieve unique field values across a collection
- *[Aggregate](operations/aggregate)* — Build multi-stage aggregation pipelines for complex data transformations
- *[Watch](operations/watch)* — Subscribe to real-time change streams
- *[Transactions](operations/transactions)* — Run multiple operations atomically with ACID guarantees
- *[Bulk Writes](operations/bulk)* — Execute mixed batches of inserts, updates, and deletes efficiently
