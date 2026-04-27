---
id: embedded
title: Embedded MongoDB (Testing)
tags: ["Embedded MongoDB", "Testing", "ScalaTest"]
---

The `mongo4cats-embedded` module starts a temporary in-process MongoDB instance for use in tests. It is built on top of [de.flapdoodle.embed.mongo](https://github.com/flapdoodle-oss/de.flapdoodle.embed.mongo) and downloads the appropriate MongoDB binary automatically on the first run (cached in `~/.embedmongo` afterwards).

## Setup

Add the dependency **scope-limited to tests**:

```scala
libraryDependencies += "io.github.kirill5k" %% "mongo4cats-embedded" % "<version>" % Test
```

## Basic usage with ScalaTest

Extend the `EmbeddedMongo` trait and wrap your test body in `withRunningEmbeddedMongo`:

```scala
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import mongo4cats.bson.Document
import mongo4cats.bson.syntax._
import mongo4cats.client.MongoClient
import mongo4cats.embedded.EmbeddedMongo
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AsyncWordSpec

class MyRepoSpec extends AsyncWordSpec with Matchers with EmbeddedMongo {

  // Default port is 27017. Override if you need a different one:
  override val mongoPort: Int = 12345

  "MyRepository" should {
    "save and find a document" in withRunningEmbeddedMongo {
      MongoClient.fromConnectionString[IO]("mongodb://localhost:12345").use { client =>
        for {
          db      <- client.getDatabase("testdb")
          coll    <- db.getCollection("items")
          doc      = Document("name" := "widget", "qty" := 10)
          _       <- coll.insertOne(doc)
          found   <- coll.find.first
        } yield found mustBe Some(doc)
      }
    }.unsafeToFuture()
  }
}
```

`withRunningEmbeddedMongo` starts the instance before the block runs and stops it after, regardless of whether the block succeeds or fails.

## Specifying host and port explicitly

If you need to run multiple embedded instances or a specific port:

```scala
"use a custom port" in withRunningEmbeddedMongo("localhost", 27099) {
  MongoClient.fromConnectionString[IO]("mongodb://localhost:27099").use { client =>
    // ...
  }
}.unsafeToFuture()
```

## Using with Cats Effect IOApp / munit-cats-effect

```scala
import cats.effect.IO
import mongo4cats.bson.Document
import mongo4cats.bson.syntax._
import mongo4cats.client.MongoClient
import mongo4cats.embedded.EmbeddedMongo
import munit.CatsEffectSuite

class MyMunitSpec extends CatsEffectSuite with EmbeddedMongo {

  override val mongoPort: Int = 27099

  test("inserts and retrieves a document") {
    withRunningEmbeddedMongo {
      MongoClient.fromConnectionString[IO]("mongodb://localhost:27099").use { client =>
        for {
          db    <- client.getDatabase("testdb")
          coll  <- db.getCollection("docs")
          doc    = Document("hello" := "world")
          _     <- coll.insertOne(doc)
          found <- coll.find.first
        } yield assertEquals(found, Some(doc))
      }
    }
  }
}
```

## ZIO variant

For ZIO-based test suites use the `mongo4cats-zio-embedded` module instead. See the [ZIO](zio) section for details.

## Notes

- Each call to `withRunningEmbeddedMongo` starts a **fresh** instance. Data does not persist between calls.
- The embedded instance is a real MongoDB process — queries behave identically to a real deployment.
- The binary is downloaded from the internet on first use. Subsequent runs use the cached binary.
- The default MongoDB version downloaded is determined by the version of `de.flapdoodle.embed.mongo` bundled with the library. Check the `build.sbt` dependencies if you need a specific version.