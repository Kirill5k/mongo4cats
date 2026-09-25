---
id: watch
title: "Watch (Change Streams)"
tags: ["watch", "change stream", "reactive"]
---

Change streams let your application react to changes in a MongoDB collection in real time. When documents are inserted, updated, replaced, or deleted, the stream emits a [change event](https://docs.mongodb.com/manual/reference/change-events/) document describing what happened.

Change streams require a MongoDB replica set or sharded cluster (MongoDB 3.6+). They are not available on standalone deployments.

## Basic usage

`watch` returns a query builder whose `.stream` method emits `ChangeStreamDocument[T]` values in an `fs2.Stream` (or `ZStream` in the ZIO module). The examples below assume `collection` is a `MongoCollection[IO, Document]`:

```scala
import cats.effect.IO
import mongo4cats.bson.Document
import mongo4cats.models.collection.ChangeStreamDocument

// Emit a change event for every change in the collection
val changes: fs2.Stream[IO, ChangeStreamDocument[Document]] = collection.watch.stream

changes.evalMap(event => IO.println(s"Change: $event")).compile.drain
```

## Filtering events

Pass an `Aggregate` pipeline to filter or transform events before they reach your application. This is more efficient than filtering on the client side, because MongoDB applies the pipeline server-side:

```scala
import mongo4cats.operations.{Aggregate, Filter}

// Only receive events whose fullDocument has an amount >= 100
val bigChanges: fs2.Stream[IO, ChangeStreamDocument[Document]] =
  collection.watch(Aggregate.matchBy(Filter.gte("fullDocument.amount", 100))).stream
```

This filter requires `fullDocument` to be present; see below for requesting it on update events.

You can also project event fields while preserving the `_id` resume token and `operationType`. The stream still emits `ChangeStreamDocument[Document]` values:

```scala
import mongo4cats.operations.Projection

val simplified: fs2.Stream[IO, ChangeStreamDocument[Document]] =
  collection
    .watch(
      Aggregate
        .matchBy(Filter.eq("operationType", "insert"))
        .project(Projection.include("_id").include("fullDocument").include("operationType"))
    )
    .stream
```

## Change event structure

Each emitted `ChangeStreamDocument[T]` maps the MongoDB [change event schema](https://docs.mongodb.com/manual/reference/change-events/) to Scala properties. `T` is the collection's document type. Key properties:

| Field | Description |
|---|---|
| `resumeToken` | A `Document` containing the token from the event's `_id` field |
| `operationType` | An `OperationType` value, such as `INSERT`, `UPDATE`, `REPLACE`, `DELETE`, or `INVALIDATE` |
| `fullDocument` | An `Option[T]` containing the document, when available |
| `documentKey` | An `Option[Document]` containing the changed document's key, including `_id` |
| `updateDescription` | An `Option[UpdateDescription]` with fields such as `updatedFields` and `removedFields` for updates |
| `namespace` | An `Option[MongoNamespace]` mapped from the event's `ns` field |
| `clusterTime` | An `Option[BsonValue]` containing the server timestamp of the change |

## Requesting the full document on updates

By default, `update` events describe the changes in `updateDescription`. Use `UPDATE_LOOKUP` to request the current document on updates. `fullDocument` remains an `Option[T]`: for example, it can be `None` if the document is deleted before the lookup completes.

```scala
import com.mongodb.client.model.changestream.FullDocument

val fullDocuments: fs2.Stream[IO, Document] =
  collection.watch.fullDocument(FullDocument.UPDATE_LOOKUP).stream
    .map(_.fullDocument)
    .unNone
```

## Resume tokens

Change streams can be resumed after a disconnect using a resume token. Each event's `resumeToken` is a mongo4cats `Document`; convert it with `toBsonDocument` for `resumeAfter`:

```scala
def changesFrom(lastToken: Option[Document]): fs2.Stream[IO, ChangeStreamDocument[Document]] = {
  val query = lastToken match {
    case Some(token) => collection.watch.resumeAfter(token.toBsonDocument)
    case None        => collection.watch
  }
  query.stream
}

val resumeTokens: fs2.Stream[IO, Document] =
  changesFrom(None).map(_.resumeToken)
```

Save `event.resumeToken` after successfully processing each event, then pass `Some(savedToken)` to `changesFrom` when reopening the stream. Passing `None` opens a stream without a resume token. Persist the saved token if it must survive an application restart.

## Using with a client session

```scala
client.startSession.use { session =>
  collection.watch(session).stream.evalMap(IO.println).compile.drain
}
```

## Watching multiple collections or a database

mongo4cats exposes `watch` on collections only. `MongoDatabase` and `MongoClient` (and their ZIO counterparts) do not expose `watch`. To observe several collections through these wrappers, open a stream for each collection and merge the streams.
