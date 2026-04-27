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

```scala
import mongo4cats.bson.Document
import mongo4cats.bson.syntax._

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

If any step fails, call `session.abortTransaction` to roll back all changes made within the transaction:

```scala
client.startSession.use { session =>
  (for {
    _ <- session.startTransaction
    _ <- coll.insertOne(session, Document("name" := "test"))
    _ <- IO.raiseError(new RuntimeException("something went wrong"))
    _ <- session.commitTransaction
  } yield ()).handleErrorWith { _ =>
    session.abortTransaction
  }
}
```

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
