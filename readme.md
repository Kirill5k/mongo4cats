mongo4cats
==========

Mongo DB scala client wrapper compatible with Cats Effect ans FS2

### Quick Start Example

```scala
import cats.effect.{ExitCode, IO, IOApp}
import mongo4cats.client.MongoClientF
import org.mongodb.scala.bson.Document
import org.mongodb.scala.model.{Filters, Sorts}

object Example extends IOApp {

  override def run(args: List[String]): IO[ExitCode] =
    MongoClientF.fromConnectionString[IO]("mongodb://localhost:27017").use { client =>
      for {
        db   <- client.getDatabase("db")
        coll <- db.getCollection("collection")
        _    <- coll.insertMany[IO]((0 to 10).map(i => Document("name" -> s"doc-$i")).toList)
        docs <- coll.find
          .filter(Filters.regex("name", "doc-\\\d+"))
          .sort(Sorts.descending("name"))
          .limit(5)
          .all[IO]
        _ <- IO(println(docs.toList))
      } yield ExitCode.Success
    }
}
```

### Dependencies

Add this to your `build.sbt` (depends on `cats-effect` and `FS2`):

```
libraryDependencies += "io.github.kirill5k" %% "mongo4cats-core" % "0.1.0"
```