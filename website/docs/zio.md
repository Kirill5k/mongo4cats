---
id: zio
title: ZIO
tags: ["ZIO", "ZIO 2"]
---

The `mongo4cats-zio` module provides `ZMongoClient`, `ZMongoDatabase`, and `ZMongoCollection` — type aliases that replace Cats Effect's `IO` with `Task` and FS2 streams with `ZStream`. The entire API surface is identical to the Cats Effect module; only the effect type changes.

## Setup

```scala
// Core ZIO integration
libraryDependencies += "io.github.kirill5k" %% "mongo4cats-zio" % "<version>"

// Embedded MongoDB for tests
libraryDependencies += "io.github.kirill5k" %% "mongo4cats-zio-embedded" % "<version>" % Test
```

Import everything from:

```scala
import mongo4cats.zio._
```

## Type aliases

| Alias | Expands to |
|---|---|
| `ZMongoClient` | `GenericMongoClient[Task, ZStream[Any, Throwable, *], Scope]` |
| `ZMongoDatabase` | `GenericMongoDatabase[Task, ZStream[Any, Throwable, *]]` |
| `ZMongoCollection[T]` | `GenericMongoCollection[Task, T, ZStream[Any, Throwable, *]]` |

## Connecting to MongoDB

`ZMongoClient.fromConnectionString` returns a `ZIO[Scope, Throwable, ZMongoClient]`, making it easy to wire it into the ZIO layer system:

```scala
import mongo4cats.zio._
import zio._

// As ZLayers for dependency injection
val clientLayer: ZLayer[Any, Throwable, ZMongoClient] =
  ZLayer.scoped(ZMongoClient.fromConnectionString("mongodb://localhost:27017"))

val dbLayer: ZLayer[ZMongoClient, Throwable, ZMongoDatabase] =
  ZLayer.fromZIO(ZIO.serviceWithZIO[ZMongoClient](_.getDatabase("my-db")))

val collectionLayer: ZLayer[ZMongoDatabase, Throwable, ZMongoCollection[Document]] =
  ZLayer.fromZIO(ZIO.serviceWithZIO[ZMongoDatabase](_.getCollection("docs")))
```

## Basic CRUD example

```scala
import mongo4cats.bson.Document
import mongo4cats.bson.syntax._
import mongo4cats.bson.ObjectId
import mongo4cats.operations.Filter
import mongo4cats.zio._
import zio._

object ZioExample extends ZIOAppDefault {
  override val run: Task[Unit] =
    ZIO.scoped {
      ZMongoClient.fromConnectionString("mongodb://localhost:27017").flatMap { client =>
        for {
          db   <- client.getDatabase("mydb")
          coll <- db.getCollection("users")
          _    <- coll.insertMany(List(
                    Document("name" := "Alice", "score" := 95),
                    Document("name" := "Bob",   "score" := 70)
                  ))
          docs <- coll.find(Filter.gte("score", 80)).all
          _    <- ZIO.foreach(docs)(d => Console.printLine(d.toString))
        } yield ()
      }
    }
}
```

## Streaming

Results are returned as `ZStream` when calling `.stream`:

```scala
import zio.stream.ZStream

val stream: ZStream[Any, Throwable, Document] =
  collection.find(Filter.gte("score", 50)).stream

stream.foreach(doc => Console.printLine(doc.toString)).provide(...)
```

## ZIO JSON integration

Use `mongo4cats-zio-json` for codec derivation with [ZIO JSON](https://zio.github.io/zio-json/) instead of Circe:

```scala
libraryDependencies += "io.github.kirill5k" %% "mongo4cats-zio-json" % "<version>"
```

```scala
import mongo4cats.zio.json._
import zio.json._

final case class User(name: String, score: Int)

object User {
  implicit val codec: JsonCodec[User] = DeriveJsonCodec.gen[User]
  implicit val mongoCodec: MongoCodecProvider[User] = deriveZioJsonCodecProvider[User]
}

val coll: Task[ZMongoCollection[User]] = db.getCollectionWithCodec[User]("users")
```

## Transactions

```scala
import mongo4cats.zio._

ZIO.scoped {
  ZMongoClient.fromConnectionString("mongodb://localhost:27017/?retryWrites=false").flatMap { client =>
    for {
      db   <- client.getDatabase("mydb")
      coll <- db.getCollection("docs")
      _ <- client.startSession.flatMap { session =>
        for {
          _ <- session.startTransaction
          _ <- coll.insertOne(session, Document("name" := "test"))
          _ <- session.commitTransaction
        } yield ()
      }
    } yield ()
  }
}
```

## Embedded MongoDB for tests

```scala
import mongo4cats.zio.embedded._
```

Extend `EmbeddedMongo` in your test suite to start a temporary MongoDB instance:

```scala
import mongo4cats.bson._
import mongo4cats.bson.syntax._
import mongo4cats.zio._
import mongo4cats.zio.embedded.EmbeddedMongo
import zio._
import zio.test._
import zio.test.Assertion._

object ZMongoCollectionSpec extends ZIOSpecDefault with EmbeddedMongo {

  override def spec = suite("ZMongoCollection")(
    test("inserts and retrieves documents") {
      withRunningEmbeddedMongo("localhost", 27017) {
        ZIO
          .serviceWithZIO[ZMongoDatabase] { db =>
            for {
              coll   <- db.getCollection("coll")
              doc     = Document("_id" := ObjectId.gen, "value" := 42)
              _      <- coll.insertOne(doc)
              result <- coll.find.all
            } yield assert(result)(equalTo(List(doc)))
          }
          .provide(
            ZLayer.scoped(ZMongoClient.fromConnectionString("mongodb://localhost:27017")),
            ZLayer.fromZIO(ZIO.serviceWithZIO[ZMongoClient](_.getDatabase("testdb")))
          )
      }
    }
  )
}
```

The `withRunningEmbeddedMongo` method starts an embedded MongoDB instance, executes the provided ZIO effect, then shuts the instance down. You can override `mongoPort` (default 27017) at the class level or pass host/port explicitly.
