mongo4cats
==========

<a href="https://typelevel.org/cats/"><img src="https://typelevel.org/cats/img/cats-badge.svg" height="40px" align="right" alt="Cats friendly" /></a>

Mongo DB client wrapper compatible with [Cats Effect](https://typelevel.org/cats-effect/) ans [Fs2](http://fs2.io/).
Available for Scala 2.12, 2.13 and 3.0.

### Dependencies

Add this to your `build.sbt` (depends on `cats-effect` and `FS2`):

```scala
libraryDependencies += "io.github.kirill5k" %% "mongo4cats-core" % "0.2.18"
libraryDependencies += "io.github.kirill5k" %% "mongo4cats-circe" % "0.2.18"// circe support
libraryDependencies += "io.github.kirill5k" %% "mongo4cats-embedded" % "0.2.18" // usefull for unit testing
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
          .filter(Filter.eq("name", "doc-0") or Filter.regex("name", "doc-[2-7]"))
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
libraryDependencies += "io.github.kirill5k" %% "mongo4cats-circe" % "0.2.18"
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

#### Running with embedded mongo

To be able to use embedded-mongodb runner in your code, the following import needs to be added to the dependency list:
```scala
libraryDependencies += "io.github.kirill5k" %% "mongo4cats-embedded" % "0.2.18"
```

Once the dependency is added, the embedded-mongodb can be brought in by extending `EmbeddedMongo` trait:

```scala
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import mongo4cats.bson.Document
import mongo4cats.client.MongoClientF
import mongo4cats.embedded.EmbeddedMongo
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AsyncWordSpec

class WithEmbeddedMongoSpec extends AsyncWordSpec with Matchers with EmbeddedMongo {

  "A MongoCollectionF" should {
    "create and retrieve documents from a db" in withRunningEmbeddedMongo("localhost", 12345) {
      MongoClientF.fromConnectionString[IO]("mongodb://localhost:12345").use { client =>
        for {
          db <- client.getDatabase("testdb")
          coll <- db.getCollection("docs")
          testDoc = Document("Hello", "World!")
          _ <- coll.insertOne[IO](testDoc)
          foundDoc <- coll.find.first[IO]
        } yield foundDoc mustBe testDoc
      }
    }.unsafeToFuture()
  }
}
```
