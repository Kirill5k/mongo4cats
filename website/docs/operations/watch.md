---
id: watch
title: "Watch (Change Streams)"
tags: ["watch", "change stream", "reactive"]
---

Change streams let your application react to changes in a MongoDB collection in real time. When documents are inserted, updated, replaced, or deleted, the stream emits a [change event](https://docs.mongodb.com/manual/reference/change-events/) document describing what happened.

Change streams require a MongoDB replica set or sharded cluster (MongoDB 3.6+). They are not available on standalone deployments.

## Basic usage

`watch` returns a `WatchQueryBuilder` whose `.stream` method produces an `fs2.Stream` (or `ZStream` in the ZIO module):

```scala
import cats.effect.IO
import mongo4cats.bson.Document

// Emit a Document for every change in the collection
val changes: fs2.Stream[IO, Document] = collection.watch.stream

changes.evalMap(event => IO.println(s"Change: $event")).compile.drain
```

## Filtering events

Pass an `Aggregate` pipeline to filter or transform events before they reach your application. This is more efficient than filtering on the client side, because MongoDB applies the pipeline server-side:

```scala
import mongo4cats.operations.{Aggregate, Filter}

// Only receive events where the "amount" field is >= 100
val bigChanges: fs2.Stream[IO, Document] =
  collection.watch(Aggregate.matchBy(Filter.gte("fullDocument.amount", 100))).stream
```

You can also transform the event shape using `$project` or `$addFields`:

```scala
val simplified: fs2.Stream[IO, Document] =
  collection
    .watch(
      Aggregate
        .matchBy(Filter.eq("operationType", "insert"))
        .project(Projection.include("fullDocument").include("operationType"))
    )
    .stream
```

## Change event structure

Each emitted document follows the MongoDB [change event schema](https://docs.mongodb.com/manual/reference/change-events/). Key fields:

| Field | Description |
|---|---|
| `operationType` | `"insert"`, `"update"`, `"replace"`, `"delete"`, `"invalidate"` |
| `fullDocument` | The full document after the change (for insert/replace; for update, only if `fullDocument` option is set) |
| `documentKey` | The `_id` of the changed document |
| `updateDescription` | For `update` events: `updatedFields` and `removedFields` |
| `ns` | Namespace: `{ db, coll }` |
| `clusterTime` | The server timestamp of the change |

## Requesting the full document on updates

By default, `update` events include only the changed fields in `updateDescription`, not the full document. To always receive the complete document after the update:

```scala
import com.mongodb.client.model.changestream.FullDocument
import com.mongodb.client.model.changestream.FullDocumentBeforeChange

collection.watch.fullDocument(FullDocument.UPDATE_LOOKUP).stream
```

## Resume tokens

Change streams can be resumed after a disconnect using a resume token. The token is available on each event:

```scala
import org.bson.BsonDocument

var lastToken: Option[BsonDocument] = None

collection.watch
  .resumeAfter(lastToken.orNull)   // pass null to start from now
  .stream
  .evalMap { event =>
    lastToken = Some(event.getResumeToken)
    IO.println(s"Event: $event")
  }
  .compile
  .drain
```

## Using with a client session

```scala
client.startSession.use { session =>
  collection.watch(session).stream.evalMap(IO.println).compile.drain
}
```

## Watching multiple collections or a database

To watch all collections in a database, call `watch` on a `MongoDatabase[F]` instance. To watch the entire deployment, call it on a `MongoClient[F]`. The API is identical.