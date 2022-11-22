mongo4cats
==========

![GitHub tag (latest SemVer)](https://img.shields.io/github/v/tag/precog/mongo4cats?label=gh&sort=semver)

MongoDB Java client wrapper compatible with [Cats-Effect](https://typelevel.org/cats-effect/) ans [Fs2](http://fs2.io/).
Available for Scala 2.12, 2.13 and 3.1.

Documentation is available on the [mongo4cats microsite](https://kirill5k.github.io/mongo4cats/docs/).

### Dependencies

Add this to your `build.sbt` (depends on `cats-effect` and `FS2`):

```scala
libraryDependencies += "io.github.kirill5k" %% "mongo4cats-core" % "<version>"
libraryDependencies += "io.github.kirill5k" %% "mongo4cats-circe" % "<version>"// circe support
libraryDependencies += "io.github.kirill5k" %% "mongo4cats-embedded" % "<version>" // embedded-mongodb
```

### Quick start

```scala
import cats.effect.{IO, IOApp}
import mongo4cats.client.MongoClient
import mongo4cats.collection.operations.Filter
import mongo4cats.bson.Document

object Quickstart extends IOApp.Simple {

  override val run: IO[Unit] =
    MongoClient.fromConnectionString[IO]("mongodb://localhost:27017").use { client =>
      for {
        db   <- client.getDatabase("testdb")
        coll <- db.getCollection("docs")
        _    <- coll.insertMany((0 to 100).map(i => Document("name" -> s"doc-$i", "index" -> i)))
        docs <- coll.find
          .filter(Filter.gte("index", 10) && Filter.regex("name", "doc-[1-9]0"))
          .sortByDesc("name")
          .limit(5)
          .all
        _ <- IO.println(docs)
      } yield ()
    }
}
```

If you find this library useful, consider giving it a ⭐!
