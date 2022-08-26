---
layout: docs
title: ZIO
number: 5
position: 5
---

## ZIO

The `mongo4cats-zio` module defines type aliases and constructors which replace Cats Effect and FS2 with ZIO and
ZIO-Streams, respectively.
Similarly, `mongo4cats-zio-embedded` brings in embedded MongoDB runner implemented with ZIO effects. This provides more
ergonomic way of integrating MongoDB with [ZIO 2](https://zio.dev).

To get access to `ZMongoClient`, `ZMongoDatabase` and `ZMongoCollection` type aliases, the following dependency needs to
be added:

```scala
libraryDependencies += "io.github.kirill5k" %% "mongo4cats-zio" % "<version>"
```

ZIO-compatible Embedded MongoDB can be brought in with:

```scala
libraryDependencies += "io.github.kirill5k" %% "mongo4cats-zio-embedded" % "<version>"
```

Next, all the essential classes will be available from:

```scala
import mongo4cats.zio._
```

### Connecting to a database and accessing collections

To establish a connection with a database, we need to create a `ZMongoClient` first.
Once the client is built, this will give us access to its databases. Furthermore, with `ZMongoDatabase` we'll be able to
browse document collections in the database.

```scala
import mongo4cats.zio._

val client = ZLayer.scoped[Any](ZMongoClient.fromConnectionString("mongodb://localhost:27017"))
val database = ZLayer.fromZIO(ZIO.serviceWithZIO[ZMongoClient](_.getDatabase("my-db")))
val collection = ZLayer.fromZIO(ZIO.serviceWithZIO[ZMongoDatabase](_.getCollection("docs")))
```

### Embedded MongoDB

ZIO-based embedded MongoDB is available from:

```scala
import mongo4cats.zio.embedded._
```

To use it in tests (or anywhere else), just extend `EmbeddedMongo` trait:

```scala
import mongo4cats.bson._
import mongo4cats.bson.syntax._
import mongo4cats.zio._
import mongo4cats.zio.embedded.EmbeddedMongo
import zio._
import zio.test._
import zio.test.Assertion._

object ZMongoCollectionSpec extends ZIOSpecDefault with EmbeddedMongo {
  override def spec = suite("A ZMongoCollection")(
    test("should store and retrieve documents") {
      withRunningEmbeddedMongo("localhost", 27017) {
        ZIO
          .serviceWithZIO[ZMongoDatabase] { db =>
            for {
              coll <- db.getCollection("coll")
              doc = Document("_id" := ObjectId.gen)
              insertResult <- coll.insertOne(doc)
              result <- coll.find.all
            } yield assert(result)(equalTo(List(doc)))
          }
          .provide(
            ZLayer.scoped(ZMongoClient.fromConnectionString(s"mongodb://localhost:27017")),
            ZLayer.fromZIO(ZIO.serviceWithZIO[ZMongoClient](_.getDatabase("my-db")))
          )
      }
    }
  )
}
```
