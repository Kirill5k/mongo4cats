---
id: watch
title: "Watch (Change Streams)"
tags: ["watch", "change stream", "reactive"]
---

Change streams let your application react to changes in a MongoDB collection, database, or deployment in real time. The stream emits [change events](https://www.mongodb.com/docs/manual/reference/change-events/) describing data changes and, optionally, additional collection and index operations.

Change streams require a MongoDB replica set or sharded cluster (MongoDB 3.6+). They are not available on standalone deployments.

## Choosing a watch scope

Collection watches emit `ChangeStreamDocument[T]` using the collection's document type. Database and client watches require MongoDB 4.0+ and emit `ChangeStreamDocument[Document]`, which accommodates different document shapes across collections:

```scala mdoc:reset
import cats.effect.IO
import fs2.Stream
import mongo4cats.bson.Document
import mongo4cats.client.MongoClient
import mongo4cats.database.MongoDatabase
import mongo4cats.models.collection.ChangeStreamDocument
import mongo4cats.operations.{Aggregate, Filter}

def databaseChanges(database: MongoDatabase[IO]): Stream[IO, ChangeStreamDocument[Document]] =
  database.watch.stream

def deploymentChanges(client: MongoClient[IO]): Stream[IO, ChangeStreamDocument[Document]] =
  client.watch.stream

// Filter using the event namespace when observing multiple collections.
def orderChanges(database: MongoDatabase[IO]): Stream[IO, ChangeStreamDocument[Document]] =
  database.watch(Aggregate.matchBy(Filter.eq("ns.coll", "orders"))).stream
```

Client and database watches supply the mongo4cats `Document` decoder when needed and preserve an explicitly registered custom `Document` codec.

A database watch observes its non-system collections. A client watch observes non-system collections across databases, excluding `admin`, `local`, and `config`. Watching these three databases directly is unsupported. The authenticated user needs `find` and `changeStream` privileges for the chosen scope. See the server's [database watch](https://www.mongodb.com/docs/manual/reference/method/db.watch/) and [deployment watch](https://www.mongodb.com/docs/manual/reference/method/mongo.watch/) documentation.

All three scopes accept `watch`, `watch(pipeline)`, `watch(session)`, and `watch(session, pipeline)`. A pipeline can be an `Aggregate` or `Seq[org.bson.conversions.Bson]`. The ZIO counterparts have the same overloads and emit a `ZStream`.

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
| `fullDocumentBeforeChange` | An `Option[T]` containing the document before an update, replacement, or deletion, when requested and available |
| `documentKey` | An `Option[Document]` containing the changed document's key, including `_id` |
| `updateDescription` | An `Option[UpdateDescription]` with fields such as `updatedFields` and `removedFields` for updates |
| `namespace` | An `Option[MongoNamespace]` mapped from the event's `ns` field |
| `clusterTime` | An `Option[BsonValue]` containing the server timestamp of the change |
| `extraElements` | Additional event fields not represented by another property |

## Requesting the full document on updates

By default, `update` events describe the changes in `updateDescription`. Use `UPDATE_LOOKUP` to request the current document on updates. `fullDocument` remains an `Option[T]`: for example, it can be `None` if the document is deleted before the lookup completes.

```scala
import com.mongodb.client.model.changestream.FullDocument

val fullDocuments: fs2.Stream[IO, Document] =
  collection.watch.fullDocument(FullDocument.UPDATE_LOOKUP).stream
    .map(_.fullDocument)
    .unNone
```

## Pre-images and expanded events

MongoDB 6.0+ supports pre-images and additional DDL events. Enable pre- and post-image storage on each source collection before the relevant writes, for example in `mongosh`:

```javascript
db.runCommand({ collMod: "orders", changeStreamPreAndPostImages: { enabled: true } })
```

Then request pre-images through the builder:

```scala mdoc:reset
import cats.effect.IO
import com.mongodb.client.model.changestream.{FullDocument, FullDocumentBeforeChange}
import mongo4cats.bson.Document
import mongo4cats.collection.MongoCollection

def withImages(collection: MongoCollection[IO, Document]) =
  collection.watch
    .fullDocumentBeforeChange(FullDocumentBeforeChange.WHEN_AVAILABLE)
    .fullDocument(FullDocument.WHEN_AVAILABLE)
    .stream

def withDdlEvents(collection: MongoCollection[IO, Document]) =
  collection.watch.showExpandedEvents(true).stream
```

`WHEN_AVAILABLE` leaves the corresponding `Option` empty when an image is unavailable; `REQUIRED` fails the stream when a required image cannot be obtained. Pre-images are absent for inserts and may expire independently of application processing. `OFF` disables pre-images. Unlike the point-in-time post-image options above, `UPDATE_LOOKUP` can return a newer version of the document. See the server's [pre- and post-image requirements](https://www.mongodb.com/docs/manual/changestreams/#change-streams-with-document-pre--and-post-images).

`showExpandedEvents(true)` includes events such as `createIndexes`, `dropIndexes`, and collection creation. These events can have no `fullDocument` or `documentKey`; inspect `namespace` and `extraElements` instead of assuming every event describes a document write. The driver's `OperationType` enum does not represent every expanded operation: the existing event model reports unrecognized operations as `OTHER` and does not expose their raw operation-type string. These options are passed to the driver; unsupported server configurations fail when the stream is consumed. See [expanded events](https://www.mongodb.com/docs/manual/reference/change-events/#expanded-events).

## Operation comments

Comments identify the watch operation in server diagnostics. Both strings and raw BSON values are supported; non-string BSON comments require MongoDB 4.4+:

```scala mdoc:reset
import cats.effect.IO
import mongo4cats.bson.Document
import mongo4cats.bson.syntax._
import mongo4cats.database.MongoDatabase

def labelledChanges(database: MongoDatabase[IO]) =
  database.watch.comment("orders-worker").stream

def structuredComment(database: MongoDatabase[IO]) =
  database.watch.comment(Document("worker" := "orders", "version" := 1).toBsonDocument).stream
```

The BSON overload takes `org.bson.BsonValue`; convert a mongo4cats `BsonValue` with `.asJava`, or a `Document` with `.toBsonDocument`. See the driver's [change-stream options](https://www.mongodb.com/docs/languages/java/reactive-streams-driver/current/logging-monitoring/change-streams/#modify-watch---behavior).

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

The token is an opaque BSON document: retain every field, rather than extracting only `_data`, and preserve the event's `_id` in any aggregation pipeline. To store a token as text, use canonical Extended JSON:

```scala mdoc:reset
import cats.effect.IO
import mongo4cats.bson.{BsonJsonMode, Document}
import mongo4cats.collection.MongoCollection

def encodeToken(token: Document): String = token.toJson(BsonJsonMode.Canonical)
def decodeToken(saved: String): Document = Document.parse(saved)

def resume(collection: MongoCollection[IO, Document], saved: String) =
  collection.watch.resumeAfter(decodeToken(saved).toBsonDocument).stream

// Use startAfter to open a new stream after an INVALIDATE event.
def restartAfterInvalidation(collection: MongoCollection[IO, Document], saved: String) =
  collection.watch.startAfter(decodeToken(saved).toBsonDocument).stream
```

Canonical Extended JSON preserves BSON type information; ordinary or relaxed JSON can lose numeric type information. Keeping the token as a nested BSON document in a checkpoint collection also preserves its types. See [Extended JSON formats](https://www.mongodb.com/docs/drivers/java/sync/current/data-formats/document-data-format-extended-json/).

Resume with the same scope, pipeline, and options used to create the token. `resumeAfter`, `startAfter`, and `startAtOperationTime` are mutually exclusive. `resumeAfter` cannot resume after an invalidate event; use `startAfter` for that case. Both require the relevant oplog history to remain available. An expired or invalid checkpoint must trigger an explicit recovery or resynchronization decision, rather than silently dropping the token. See [resuming a change stream](https://www.mongodb.com/docs/manual/changestreams/#resume-a-change-stream).

## Durable checkpoints

Persist a checkpoint **after** successful processing, then load it when reopening the stream. The following Cats Effect example keeps one checkpoint document in a dedicated collection and waits for a majority-acknowledged, journaled write. It excludes checkpoint writes from its database watch to prevent the checkpoint updates from generating an endless sequence of events:

```scala mdoc:reset
import cats.effect.IO
import cats.syntax.all._
import com.mongodb.{ReadConcern, ReadPreference, WriteConcern}
import mongo4cats.bson.Document
import mongo4cats.bson.syntax._
import mongo4cats.database.MongoDatabase
import mongo4cats.models.collection.{ChangeStreamDocument, ReplaceOptions}
import mongo4cats.operations.{Aggregate, Filter}

def consume(database: MongoDatabase[IO])(
    process: ChangeStreamDocument[Document] => IO[Unit]
): IO[Unit] = {
  val checkpointCollection = "_change_stream_checkpoints"
  val checkpointId = Filter.eq("_id", "my-db-events-v1")
  val pipeline = Aggregate.matchBy(Filter.ne("ns.coll", checkpointCollection))

  database.getCollection(checkpointCollection).flatMap { collection =>
    val checkpoints = collection
      .withReadPreference(ReadPreference.primary())
      .withReadConcern(ReadConcern.MAJORITY)
      .withWriteConcern(WriteConcern.MAJORITY.withJournal(true))

    def save(token: Document): IO[Unit] =
      checkpoints.replaceOne(
        checkpointId,
        Document("_id" := "my-db-events-v1", "resumeToken" := token),
        ReplaceOptions(upsert = true)
      ).void

    for {
      record <- checkpoints.find(checkpointId).first
      token <- record.traverse { saved =>
        IO.fromOption(saved.getDocument("resumeToken"))(
          new IllegalArgumentException("Checkpoint has no BSON resumeToken document")
        )
      }
      query = database.watch(pipeline)
      resumed = token.fold(query)(saved => query.resumeAfter(saved.toBsonDocument))
      _ <- resumed.stream.evalMap(event => process(event) *> save(event.resumeToken)).compile.drain
    } yield ()
  }
}
```

The runnable [Cats Effect example](https://github.com/Kirill5k/mongo4cats/blob/master/examples/src/main/scala/mongo4cats/examples/DurableWatch.scala) and [ZIO example](https://github.com/Kirill5k/mongo4cats/blob/master/examples/src/main/scala/mongo4cats/examples/ZioDurableWatch.scala) use this checkpoint format. The ZIO version performs the same load and majority write operations, and consumes sequentially using `mapZIO`:

```scala mdoc:reset
import mongo4cats.bson.Document
import mongo4cats.models.collection.ChangeStreamDocument
import zio.Task
import zio.stream.ZStream

def processAndCheckpoint(
    changes: ZStream[Any, Throwable, ChangeStreamDocument[Document]],
    process: ChangeStreamDocument[Document] => Task[Unit],
    save: Document => Task[Unit]
): Task[Unit] =
  changes.mapZIO(event => process(event) *> save(event.resumeToken)).runDrain
```

Resuming from an existing checkpoint delivers **at least once**, not exactly once: a crash after processing but before checkpoint persistence can replay the event. Make handlers idempotent. Processing, loading, and saving failures propagate; the examples never advance a checkpoint after a failed handler or continue after a failed save. Sequential `evalMap` / `mapZIO` prevents later completions from checkpointing past an unfinished event.

Use one active consumer per checkpoint id and reserve the checkpoint collection for checkpoints. Give different pipelines or scopes different checkpoint ids. On a first run with no saved checkpoint, the watch begins at its normal current position; it does not backfill existing data. A failure before the first checkpoint leaves no durable starting position for replay: applications requiring recovery from the very first event must bootstrap a persisted starting token or operation time. If processing writes into the watched database, exclude those destination collections too. Handle database drops, invalidation, expired oplog history, and initial backfills explicitly for your application. The stream exposes tokens on emitted events; these examples cannot persist a newer token while no events are emitted.

## Using with a client session

```scala
client.startSession.use { session =>
  collection.watch(session).stream.evalMap(IO.println).compile.drain
}
```

The same session overloads work on `database.watch(session)` and `client.watch(session)`. The session must belong to the client owning the watched scope and remain open while the stream is consumed. Change streams cannot run inside transactions.

## Custom wrapper implementations

Applications using the supplied Cats Effect or ZIO wrappers can use these methods directly. Custom subclasses of `GenericMongoClient` or `GenericMongoDatabase` must implement the four new watch methods: the `Seq[Bson]` and `Aggregate` variants, each with and without a `ClientSession`. The no-pipeline and session-only overloads delegate to those methods. Recompile custom implementations when upgrading.
