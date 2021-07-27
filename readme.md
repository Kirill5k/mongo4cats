mongo4cats
==========

<a href="https://typelevel.org/cats/"><img src="https://typelevel.org/cats/img/cats-badge.svg" height="40px" align="right" alt="Cats friendly" /></a>

Mongo DB client wrapper compatible with [Cats Effect](https://typelevel.org/cats-effect/) ans [Fs2](http://fs2.io/).
Available for Scala 2.12, 2.13 and 3.0.

### Dependencies

Add this to your `build.sbt` (depends on `cats-effect` and `FS2`):

```scala
libraryDependencies += "io.github.kirill5k" %% "mongo4cats-core" % "0.2.15"
libraryDependencies += "io.github.kirill5k" %% "mongo4cats-circe" % "0.2.15"// circe support
```

### Quick Start Examples

#### Working with plain JSON

```scala
import cats.effect.{IO, IOApp}
import mongo4cats.client.MongoClientF
import mongo4cats.database.operations.{Filter, Update}
import mongo4cats.bson.Document

object DocumentFindAndUpdate extends IOApp.Simple {

  val json =
    """{
      |"firstName": "John",
      |"lastName": "Bloggs",
      |"dob": "1970-01-01"
      |}""".stripMargin

  val run: IO[Unit] =
    MongoClientF.fromConnectionString[IO]("mongodb://localhost:27017").use { client =>
      for {
        db      <- client.getDatabase("testdb")
        coll    <- db.getCollection("jsoncoll")
        _       <- coll.insertOne[IO](Document.parse(json))
        filterQuery = Filter.eq("lastName", "Bloggs") and Filter.eq("firstName", "John")
        updateQuery = Update.set("dob", "2020-01-01").rename("firstName", "name").currentTimestamp("updatedAt").unset("lastName")
        old     <- coll.findOneAndUpdate[IO](filterQuery, updateQuery)
        updated <- coll.find.first[IO]
        _       <- IO.println(s"old: ${old.toJson()}\nupdated: ${updated.toJson()}")
      } yield ()
    }
}
```

#### Working with documents

```scala
import cats.effect.{IO, IOApp}
import mongo4cats.client.MongoClientF
import mongo4cats.database.operations.Filter
import mongo4cats.bson.Document

object FilteringAndSorting extends IOApp.Simple {

  def genDocs(n: Int): Seq[Document] =
    (0 to n).map(i => Document("name", s"doc-$i"))

  override val run: IO[Unit] =
    MongoClientF.fromConnectionString[IO]("mongodb://localhost:27017").use { client =>
      for {
        db   <- client.getDatabase("testdb")
        coll <- db.getCollection("docs")
        _    <- coll.insertMany[IO](genDocs(10))
        docs <- coll.find
          .filter(Filter.exists("name") and Filter.regex("name", "doc-[2-7]"))
          .sortByDesc("name")
          .limit(5)
          .all[IO]
        _ <- IO.println(docs)
      } yield ()
    }
}
```

#### Using circe for encoding and decoding documents into case classes

Only mongo4cats-circe is required for basic interop with circe:
```scala
libraryDependencies += "io.github.kirill5k" %% "mongo4cats-circe" % "0.2.15"
```

In order to obtain mongo collection with circe codecs, the following import is required:
```scala
import mongo4cats.circe._
```

The complete working example is presented below

```scala
import cats.effect.{IO, IOApp}
import io.circe.generic.auto._
import mongo4cats.client.MongoClientF
import mongo4cats.circe._

import java.time.Instant

object Example extends IOApp.Simple {

  final case class Address(city: String, country: String)
  final case class Person(firstName: String, lastName: String, address: Address, registrationDate: Instant)

  val run: IO[Unit] =
    MongoClientF.fromConnectionString[IO]("mongodb://localhost:27017").use { client =>
      for {
        db   <- client.getDatabase("testdb")
        coll <- db.getCollectionWithCodec[Person]("people")
        _    <- coll.insertOne[IO](Person("John", "Bloggs", Address("New-York", "USA"), Instant.now()))
        docs <- coll.find.stream[IO].compile.toList
        _    <- IO.println(docs)
      } yield ()
    }
}
```

