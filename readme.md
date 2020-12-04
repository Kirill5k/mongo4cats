mongo4cats
==========

<a href="https://typelevel.org/cats/"><img src="https://typelevel.org/cats/img/cats-badge.svg" height="40px" align="right" alt="Cats friendly" /></a>

Mongo DB scala client wrapper compatible with [Cats Effect](https://typelevel.org/cats-effect/) ans [Fs2](http://fs2.io/).
Supports Scala 2.12 and 2.13.

### Dependencies

Add this to your `build.sbt` (depends on `cats-effect` and `FS2`):

```
libraryDependencies += "io.github.kirill5k" %% "mongo4cats-core" % "0.1.3"
```

### Quick Start Examples

#### Working with documents

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
          .filter(Filters.regex("name", "doc-\\d+"))
          .sort(Sorts.descending("name"))
          .limit(5)
          .all[IO]
        _ <- IO(println(docs.toList))
      } yield ExitCode.Success
    }
}
```

#### Working with case classes

```scala
import cats.effect.{ExitCode, IO, IOApp}
import mongo4cats.client.MongoClientF
import org.mongodb.scala.bson.codecs.Macros._
import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}
import org.mongodb.scala.MongoClient.DEFAULT_CODEC_REGISTRY
import org.mongodb.scala.bson.ObjectId


object Example extends IOApp {

  final case class Person(_id: ObjectId, firstName: String, lastName: String)

  val personCodecRegistry = fromRegistries(fromProviders(classOf[Person]), DEFAULT_CODEC_REGISTRY)

  override def run(args: List[String]): IO[ExitCode] =
    MongoClientF.fromConnectionString[IO]("mongodb://localhost:27017").use { client =>
      for {
        db   <- client.getDatabase("db")
        coll <- db.getCollection[Person]("collection", personCodecRegistry)
        _    <- coll.insertOne[IO](Person(new ObjectId(), "John", "Bloggs"))
        docs <- coll.find.stream[IO].compile.toList
        _    <- IO(println(docs))
      } yield ExitCode.Success
    }
}
```

Refer to the official documentation for more sophisticated examples on working with case classes: https://mongodb.github.io/mongo-scala-driver/2.9/bson/macros/
