---
id: index
title: Getting started
tags: ["Getting Started"]
---

[![Maven Central](https://img.shields.io/maven-central/v/io.github.kirill5k/mongo4cats-core_2.13.svg)](http://search.maven.org/#search%7Cga%7C1%mongo4cats-core)

mongo4cats is a Scala wrapper around the [MongoDB Java Reactive Streams driver](https://www.mongodb.com/docs/drivers/reactive-streams/). It provides a purely functional, type-safe API for working with MongoDB, built on top of [Cats Effect](https://typelevel.org/cats-effect/) and [FS2](https://fs2.io/). A [ZIO 2](https://zio.dev) integration is also available.

### Features

- Purely functional API using `IO` / `ZIO`
- Streaming support via FS2 / ZIO Streams for large result sets
- Type-safe query builders for filters, projections, sorts, updates, and aggregations
- Automatic BSON codec derivation via [Circe](https://circe.github.io/circe/) or [ZIO JSON](https://zio.github.io/zio-json/)
- Transaction support
- Bulk write operations
- Change stream (watch) support
- Embedded MongoDB for testing

### Supported Scala versions

Scala 2.12, 2.13, and 3 are all supported.

### Dependencies

Add the core module to your `build.sbt`:

```scala
libraryDependencies += "io.github.kirill5k" %% "mongo4cats-core" % "<version>"
```

For automatic BSON codec derivation via Circe:

```scala
libraryDependencies += "io.github.kirill5k" %% "mongo4cats-circe" % "<version>"
```

For automatic BSON codec derivation via ZIO JSON:

```scala
libraryDependencies += "io.github.kirill5k" %% "mongo4cats-zio-json" % "<version>"
```

For embedded MongoDB in tests (Cats Effect):

```scala
libraryDependencies += "io.github.kirill5k" %% "mongo4cats-embedded" % "<version>" % Test
```

For ZIO 2 integration:

```scala
libraryDependencies += "io.github.kirill5k" %% "mongo4cats-zio" % "<version>"
libraryDependencies += "io.github.kirill5k" %% "mongo4cats-zio-embedded" % "<version>" % Test
```

### Quick example

```scala
import cats.effect.{IO, IOApp}
import mongo4cats.bson.Document
import mongo4cats.bson.syntax._
import mongo4cats.client.MongoClient
import mongo4cats.operations.Filter

object QuickStart extends IOApp.Simple {
  override val run: IO[Unit] =
    MongoClient.fromConnectionString[IO]("mongodb://localhost:27017").use { client =>
      for {
        db   <- client.getDatabase("mydb")
        coll <- db.getCollection("users")
        _    <- coll.insertMany(List(
                  Document("name" := "Alice", "age" := 30),
                  Document("name" := "Bob",   "age" := 25)
                ))
        docs <- coll.find(Filter.gte("age", 28)).all
        _    <- IO.println(s"Found: $docs")
      } yield ()
    }
}
```

### Next steps

- *[Making a connection](gettingstarted/connection)*
- *[Getting a collection](gettingstarted/collection)*
- *[Working with documents](gettingstarted/documents)*
- *[Operations overview](operations)*
- *[Circe codec derivation](circe)*
- *[ZIO integration](zio)*
- *[Embedded MongoDB for testing](embedded)*
