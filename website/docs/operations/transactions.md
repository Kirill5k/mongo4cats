---
id: transactions
title: "Transactions"
tags: ["transactions", "ACID", "ClientSession"]
---

MongoDB multi-document transactions provide ACID guarantees across multiple operations and collections. They require a replica set (MongoDB 4.0+) or a sharded cluster (MongoDB 4.2+). Standalone deployments do not support transactions.

## Managed transactions

Managed transactions handle startup, commit, rollback, and bounded retries with Cats Effect. `MongoClient.transact` and `ClientSession.withTransaction` are native methods and require no transaction syntax imports:

```scala
import cats.effect.IO
import mongo4cats.client.MongoClient
import mongo4cats.operations.{Filter, Update}

MongoClient.fromConnectionString[IO]("mongodb://localhost:27017/?retryWrites=false").use { client =>
  for {
    db   <- client.getDatabase("mydb")
    coll <- db.getCollection("accounts")
    transferred <- client.transact { session =>
      for {
        _ <- coll.updateOne(session, Filter.eq("name", "Alice"), Update.inc("balance", -100))
        _ <- coll.updateOne(session, Filter.eq("name", "Bob"), Update.inc("balance", 100))
      } yield 100
    }
  } yield transferred
}
```

`client.transact` acquires one session, reuses it across retry attempts, and closes it when the effect finishes. The result is the callback's value after a successful commit. Both callback construction and driver calls are deferred until the returned effect runs; running the effect again starts with a fresh retry budget.

Use `session.withTransaction` when the caller already owns a session. It requires no transaction syntax import:

```scala
client.startSession.use { session =>
  session.withTransaction {
    coll.updateOne(session, Filter.eq("name", "Alice"), Update.inc("balance", 100))
  }
}
```

`withTransaction` leaves the session open for its owner. Starting a managed transaction on an already-active session fails without aborting the existing transaction. Do not nest transactions or share a session between concurrent operations; execute the operations in a callback sequentially. Pass the session explicitly to every operation that should participate.

Custom `GenericMongoClient` subclasses must implement the configured `transact` overload, and custom `ClientSession` subclasses must implement the configured `withTransaction` overload when upgrading. The existing client type aliases are unchanged.

Normally, let the helper commit or abort. If the callback manually ends the transaction, the helper skips its automatic commit.

### Retry policy

Managed transactions distinguish two kinds of retry:

| Failure | Managed behavior |
|---|---|
| `TransientTransactionError` from the body or commit | Retry the whole transaction, including its callback, after applicable rollback. |
| `UnknownTransactionCommitResult` from commit | Retry only commit on the same session, even if the driver reports the transaction as inactive. |
| Both labels on a commit failure | Retry only commit; the unknown result takes precedence. |
| Operation timeout, commit `MaxTimeMSExpired` (code 50), or an unlabeled failure | Propagate the failure. |

**Callbacks can run more than once.** Keep external effects safe to repeat: sending an email or making a remote payment is not rolled back with MongoDB writes. Put actions that should happen once after `transact` completes, or use an application-level idempotency strategy.

Propagate database operation failures from the callback. Swallowing them prevents the helper from applying its retry policy and can leave the transaction unusable.

The default `TransactionRetryPolicy` permits retries within 120 seconds, using exponential delays from 10 milliseconds up to one second. Both retry loops share one monotonic time budget. The budget is checked before scheduling a retry and again before executing it; exhaustion returns the last original error. It limits retries, not the running time of the callback or an in-flight driver operation.

```scala
import com.mongodb.{ReadConcern, WriteConcern}
import mongo4cats.models.client.{ClientSessionOptions, TransactionOptions, TransactionRetryPolicy}
import scala.concurrent.duration._

val txOptions = TransactionOptions.builder
  .readConcern(ReadConcern.SNAPSHOT)
  .writeConcern(WriteConcern.MAJORITY)
  .build()

val retryPolicy = TransactionRetryPolicy(
  maxDuration = 30.seconds,
  initialDelay = 20.millis,
  maxDelay = 500.millis
)

client.transact(
  options = txOptions,
  retryPolicy = retryPolicy,
  sessionOptions = ClientSessionOptions()
) { session =>
  coll.updateOne(session, Filter.eq("name", "Alice"), Update.inc("balance", 100))
}

// The same configuration is available on a caller-owned session.
session.withTransaction(options = txOptions, retryPolicy = TransactionRetryPolicy.none) {
  coll.updateOne(session, Filter.eq("name", "Bob"), Update.inc("balance", 100))
}
```

`TransactionRetryPolicy.none`, or a zero `maxDuration`, disables managed retries. Policy durations are validated when the policy is constructed. Individual transactional writes are not made retryable by `retryWrites`; the driver can still retry commit operations even with `retryWrites=false`.

### Failures and cancellation

If the body fails, the helper aborts an active transaction and re-raises the original error. A rollback failure is attached as a suppressed exception to that error and stops retries. Cancellation also attempts rollback where applicable and preserves cancellation; rollback failures during cancellation are reported through the Cats Effect runtime. After an uncertain commit, the helper does not attempt rollback: the transaction might already have committed.

Startup, rollback, and each commit attempt are masked from cancellation; callbacks and retry delays remain cancelable. Cancellation requested during commit can therefore wait for the attempt to finish, and the transaction may commit despite the cancellation request. Configure driver timeouts, including a commit time limit where appropriate; an outer effect timeout or the retry budget cannot interrupt a masked driver operation. An unknown commit result remains uncertain if its retries are exhausted or canceled.

The [ZIO integration](../zio.md#transactions) provides the same native transaction methods and retry policy, with support for callbacks that require a ZIO environment.

To opt into the repository's transaction integration tests, set `MONGO4CATS_TRANSACTION_URI` to a test replica set or sharded cluster URI; the tests create and drop uniquely named collections in the `mongo4cats_transaction_tests` database.

## Starting a session

A `ClientSession[F]` is required to run operations inside a transaction. Obtain one from the client using `startSession`, which returns a `Resource[F, ClientSession[F]]`:

```scala
import cats.effect.IO
import mongo4cats.client.MongoClient

MongoClient.fromConnectionString[IO]("mongodb://localhost:27017/?retryWrites=false").use { client =>
  client.startSession.use { session =>
    // use session here
    IO.unit
  }
}
```

## Manual transaction lifecycle

The successful path starts a transaction, performs operations with the session, and commits. The next example adds rollback and cancellation handling.

```scala
import mongo4cats.operations.{Filter, Update}

MongoClient.fromConnectionString[IO]("mongodb://localhost:27017/?retryWrites=false").use { client =>
  for {
    db   <- client.getDatabase("mydb")
    coll <- db.getCollection("accounts")
    _ <- client.startSession.use { session =>
      for {
        _      <- session.startTransaction
        _      <- coll.updateOne(session, Filter.eq("name", "Alice"), Update.inc("balance", -100))
        _      <- coll.updateOne(session, Filter.eq("name", "Bob"),   Update.inc("balance",  100))
        _      <- session.commitTransaction
      } yield ()
    }
  } yield ()
}
```

If the transaction body fails, attempt rollback and then re-raise the original error. Returning only `session.abortTransaction` from an error handler would turn a failed operation into a successful effect. If rollback also fails, retain that failure as a suppressed exception on the original error:

```scala
import mongo4cats.bson.Document
import mongo4cats.bson.syntax._

client.startSession.use { session =>
  val abort = IO.defer(session.abortTransaction)

  IO.uncancelable { poll =>
    session.startTransaction *>
      poll {
        for {
          _ <- coll.insertOne(session, Document("name" := "test"))
          _ <- IO.raiseError[Unit](new RuntimeException("something went wrong"))
        } yield ()
      }.onCancel(abort)
        .handleErrorWith { error =>
          abort.attempt.flatMap {
            case Left(rollbackError) if rollbackError ne error =>
              IO(error.addSuppressed(rollbackError))
            case _ => IO.unit
          } *> IO.raiseError[Unit](error)
        } *>
      session.commitTransaction
  }
}
```

The handler covers the transaction body after `startTransaction` succeeds. `commitTransaction` is outside that handler: a failed commit can have an unknown outcome, so attempting an abort does not prove that the transaction was rolled back.

### Cancellation

Cancellation is distinct from failure in Cats Effect, so `handleErrorWith` alone does not handle it. Here `poll` makes the body cancelable, and `onCancel` attempts rollback before the session resource is released. The effect remains canceled; an abort-finalizer failure is reported through the IO runtime rather than replacing cancellation. See the [Cats Effect cancellation contract](https://typelevel.org/cats-effect/api/3.x/cats/effect/kernel/MonadCancel.html).

Transaction startup, rollback, and commit are masked from cancellation in this example. A cancellation request during commit may therefore wait for commit to finish, and the transaction may commit even though the caller requested cancellation. Configure driver timeouts, including a commit time limit where appropriate, so masked database operations cannot wait indefinitely; an outer effect timeout alone cannot interrupt them.

### Retrying transactions

The manual session methods expose MongoDB's core transaction API and do not add a retry loop themselves. Prefer the managed helpers above, or apply an explicit policy based on the error labels described in [MongoDB's transaction error handling guidance](https://www.mongodb.com/docs/manual/core/transactions-in-applications/):

- `TransientTransactionError`: retry the entire transaction from `startTransaction`, rerunning its body.
- `UnknownTransactionCommitResult`: retry `commitTransaction` for the same transaction and session, inside the session resource scope. Do not rerun the body merely because the commit result is unknown; it may already have committed.
- Other failures: propagate the error unless a separate application policy says it is retryable.

Use bounded attempts with backoff and an overall deadline. Keep retryable bodies safe to repeat: external actions such as sending email are not rolled back with MongoDB writes. Cancellation should stop application retries. Individual transactional writes are not made retryable by `retryWrites`; the driver can still retry commit operations even with `retryWrites=false`.

## Passing the session to collection operations

Pass the `ClientSession[F]` as the first argument to session-aware collection methods:

```scala
// Insert within a transaction
coll.insertOne(session, document)

// Find within a transaction
coll.find(session, Filter.eq("status", "pending")).all

// Update within a transaction
coll.updateMany(session, Filter.eq("status", "pending"), Update.set("status", "processed"))

// Delete within a transaction
coll.deleteOne(session, Filter.eq("_id", docId))
```

Session overloads accept raw `Bson` filters and update documents as well as the `Filter` and `Update` builders. Aggregation and change-stream pipelines accept `Seq[Bson]` alongside `Aggregate`; `aggregateWithCodec` and `distinctWithCodec` also support sessions with raw BSON inputs. Index creation and removal, replacements, exact counts, and atomic find-and-modify operations follow the same session-first convention. Estimated document counts have no session overload.

## Full example: abort vs commit

```scala
import cats.effect.{IO, IOApp}
import cats.syntax.foldable._
import mongo4cats.bson.Document
import mongo4cats.bson.syntax._
import mongo4cats.client.MongoClient

object TransactionsExample extends IOApp.Simple {
  override val run: IO[Unit] =
    MongoClient.fromConnectionString[IO]("mongodb://localhost:27017/?retryWrites=false").use { client =>
      for {
        db   <- client.getDatabase("mydb")
        coll <- db.getCollection("docs")
        _ <- client.startSession.use { session =>
          for {
            // --- Aborted transaction ---
            _      <- session.startTransaction
            _      <- (0 to 9).toList.traverse_(i => coll.insertOne(session, Document("n" := i)))
            _      <- session.abortTransaction
            count1 <- coll.count
            _      <- IO.println(s"After abort: $count1 documents (should be 0)")

            // --- Committed transaction ---
            _      <- session.startTransaction
            _      <- (0 to 9).toList.traverse_(i => coll.insertOne(session, Document("n" := i)))
            _      <- session.commitTransaction
            count2 <- coll.count
            _      <- IO.println(s"After commit: $count2 documents (should be 10)")
          } yield ()
        }
      } yield ()
    }
}
```

## Session options

```scala
import mongo4cats.models.client.ClientSessionOptions
import com.mongodb.{ReadConcern, WriteConcern, ReadPreference, TransactionOptions}

val sessionOptions = ClientSessionOptions()

val txOptions = TransactionOptions.builder()
  .readConcern(ReadConcern.SNAPSHOT)
  .writeConcern(WriteConcern.MAJORITY)
  .readPreference(ReadPreference.primary())
  .build()

client.startSession(sessionOptions).use { session =>
  session.startTransaction(txOptions) *> /* ... */ session.commitTransaction
}
```
