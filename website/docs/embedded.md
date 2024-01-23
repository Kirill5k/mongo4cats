---
layout: docs
title: Embedded MongoDB
number: 3
position: 3
---

## Embedded MongoDB

The main goal of `mongo4cats-embedded` module is to provide a way of making quick and easy connections to a database instance that will be disposed afterwards.
One of the use-cases for such scenarios is unit testing where you would just need to make 1 or 2 connections to a fresh database instance to test your queries and be done with it.

To enable embedded-mongo support, a dependency has to be added in the `build.sbt`:
```scala
libraryDependencies += "io.github.kirill5k" %% "mongo4cats-embedded" % "<version>"
```

Once the dependency is added, the embedded-mongodb can be brought in by extending `EmbeddedMongo` trait from `mongo4cats.embedded` package:

```scala
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import mongo4cats.bson.Document
import mongo4cats.bsom.syntax._
import mongo4cats.client.MongoClient
import mongo4cats.embedded.EmbeddedMongo
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AsyncWordSpec

class WithEmbeddedMongoSpec extends AsyncWordSpec with Matchers with EmbeddedMongo {

  // by default, MongoDB instance will be accessible on 27017 port, which can be overridden:
  override val mongoPort: Int = 12345

  "A MongoCollection" should {
    "create and retrieve documents from a db" in withRunningEmbeddedMongo {
      MongoClient.fromConnectionString[IO]("mongodb://localhost:12345").use { client =>
        for {
          db <- client.getDatabase("testdb")
          coll <- db.getCollection("docs")
          testDoc = Document("Hello" := "World!")
          _ <- coll.insertOne(testDoc)
          foundDoc <- coll.find.first
        } yield foundDoc mustBe Some(testDoc)
      }
    }.unsafeToFuture()

    // or connection properties can be passed explicitly
    "start instance on different port" in withRunningEmbeddedMongo("localhost", 12355) {
      MongoClient.fromConnectionString[IO]("mongodb://localhost:12355").use { client =>
        for {
          db <- client.getDatabase("testdb")
          coll <- db.getCollection("docs")
          testDoc = Document("Hello" := "World!")
          _ <- coll.insertOne(testDoc)
          foundDoc <- coll.find.first
        } yield foundDoc mustBe Some(testDoc)
      }
    }.unsafeToFuture()
  }
}

```