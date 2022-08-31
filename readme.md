mongo4cats
==========

[![Maven Central](https://img.shields.io/maven-central/v/io.github.kirill5k/mongo4cats-core_2.13.svg)](http://search.maven.org/#search%7Cga%7C1%mongo4cats-core)
<a href="https://typelevel.org/cats/"><img src="https://typelevel.org/cats/img/cats-badge.svg" height="40px" align="right" alt="Cats friendly" /></a>

MongoDB Java client wrapper compatible with [Cats-Effect](https://typelevel.org/cats-effect/) and [Fs2](http://fs2.io/).
Available for Scala 2.12, 2.13 and 3.1.

Documentation is available on the [mongo4cats microsite](https://kirill5k.github.io/mongo4cats/docs/).

### Dependencies

Add this to your `build.sbt` (depends on `cats-effect` and `FS2`):

```scala
libraryDependencies += "io.github.kirill5k" %% "mongo4cats-core" % "<version>"
libraryDependencies += "io.github.kirill5k" %% "mongo4cats-embedded" % "<version>" % Test
```

Alternatively, for `ZIO 2`, add this:

```scala
libraryDependencies += "io.github.kirill5k" %% "mongo4cats-zio" % "<version>"
libraryDependencies += "io.github.kirill5k" %% "mongo4cats-zio-embedded" % "<version>" % Test
```

Optional support for `circe` can be enabled with:

```scala
libraryDependencies += "io.github.kirill5k" %% "mongo4cats-circe" % "<version>"
```

### Quick start with Cats Effect

```scala
import cats.effect.{IO, IOApp}
import mongo4cats.client.MongoClient
import mongo4cats.operations.{Filter, Projection}
import mongo4cats.bson.Document
import mongo4cats.bson.syntax._

object Quickstart extends IOApp.Simple {

  override val run: IO[Unit] =
    MongoClient.fromConnectionString[IO]("mongodb://localhost:27017").use { client =>
      for {
        db   <- client.getDatabase("my-db")
        coll <- db.getCollection("docs")
        _    <- coll.insertMany((0 to 100).map(i => Document("name" := s"doc-$i", "index" := i)))
        docs <- coll.find
          .filter(Filter.gte("index", 10) && Filter.regex("name", "doc-[1-9]0"))
          .projection(Projection.excludeId)
          .sortByDesc("name")
          .limit(5)
          .all
        _ <- IO.println(docs.mkString("[\n", ",\n", "\n]"))
      } yield ()
    }
}
```

### Quick start with ZIO

```scala
import mongo4cats.bson.Document
import mongo4cats.bson.syntax._
import mongo4cats.operations.{Filter, Projection}
import mongo4cats.zio.{ZMongoClient, ZMongoCollection, ZMongoDatabase}
import zio._

object Zio extends ZIOAppDefault {

  val client     = ZLayer.scoped[Any](ZMongoClient.fromConnectionString("mongodb://localhost:27017"))
  val database   = ZLayer.fromZIO(ZIO.serviceWithZIO[ZMongoClient](_.getDatabase("my-db")))
  val collection = ZLayer.fromZIO(ZIO.serviceWithZIO[ZMongoDatabase](_.getCollection("docs")))

  val program = for {
    coll <- ZIO.service[ZMongoCollection[Document]]
    _    <- coll.insertMany((0 to 100).map(i => Document("name" := s"doc-$i", "index" := i)))
    docs <- coll.find
      .filter(Filter.gte("index", 10) && Filter.regex("name", "doc-[1-9]0"))
      .projection(Projection.excludeId)
      .sortByDesc("name")
      .limit(5)
      .all
    _ <- Console.printLine(docs.mkString("[\n", ",\n", "\n]"))
  } yield ()

  override def run = program.provide(client, database, collection)
}
```

If you find this library useful, consider giving it a ⭐!