---
id: transactions
title: "Transactions"
tags: ["transactions", "ACID", "ClientSession"]
---

MongoDB multi-document transactions provide ACID guarantees across multiple operations and collections. They require a replica set (MongoDB 4.0+) or a sharded cluster (MongoDB 4.2+). Standalone deployments do not support transactions.

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

## Transaction lifecycle

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

These session methods expose MongoDB's core transaction API; mongo4cats does not add a transaction retry loop. Apply an explicit policy based on the error labels described in [MongoDB's transaction error handling guidance](https://www.mongodb.com/docs/manual/core/transactions-in-applications/):

- `TransientTransactionError`: retry the entire transaction from `startTransaction`, rerunning its body.
- `UnknownTransactionCommitResult`: retry `commitTransaction` for the same transaction and session, inside the session resource scope. Do not rerun the body merely because the commit result is unknown; it may already have committed.
- Other failures: propagate the error unless a separate application policy says it is retryable.

Use bounded attempts with backoff and an overall deadline. Keep retryable bodies safe to repeat: external actions such as sending email are not rolled back with MongoDB writes. Cancellation should stop application retries. Individual transactional writes are not made retryable by `retryWrites`; the driver can still retry commit operations even with `retryWrites=false`.

## Passing the session to collection operations

Every CRUD method on `MongoCollection[F, T]` has a session-aware overload. Pass the `ClientSession[F]` as the first argument:

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
