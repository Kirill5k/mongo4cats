mongo4cats
==========

<a href="https://typelevel.org/cats/"><img src="https://typelevel.org/cats/img/cats-badge.svg" height="40px" align="right" alt="Cats friendly" /></a>

Mongo DB scala client wrapper compatible with [Cats Effect](https://typelevel.org/cats-effect/) ans [Fs2](http://fs2.io/).
Available for Scala 2.12 and Scala 2.13.

### Dependencies

Add this to your `build.sbt` (depends on `cats-effect` and `FS2`):

```
libraryDependencies += "io.github.kirill5k" %% "mongo4cats-core" % "0.2.5"
libraryDependencies += "io.github.kirill5k" %% "mongo4cats-circe" % "0.2.5" // circe support
```

### Quick Start Examples

#### Working with JSON

```scala
import cats.effect.{IO, IOApp}
import mongo4cats.client.MongoClientF
import org.mongodb.scala.bson.Document
import org.mongodb.scala.model.{Filters, Updates}

object Example extends IOApp.Simple {

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
        _       <- coll.insertOne[IO](Document(json))
        old     <- coll.findOneAndUpdate[IO](Filters.equal("lastName", "Bloggs"), Updates.set("dob", "2020-01-01"))
        updated <- coll.find.first[IO]
        _       <- IO.println(old.toJson(), updated.toJson())
      } yield ()
    }
}
```

#### Working with documents

```scala
import cats.effect.{IO, IOApp}
import mongo4cats.client.MongoClientF
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.{Filters, Sorts}

object Example extends IOApp.Simple {

  val run: IO[Unit] =
    MongoClientF.fromConnectionString[IO]("mongodb://localhost:27017").use { client =>
      for {
        db   <- client.getDatabase("testdb")
        coll <- db.getCollection("docs")
        newDocs = (0 to 10).map(i => Document("name" -> s"doc-$i"))
        _ <- coll.insertMany[IO](newDocs)
        docs <- coll.find
          .filter(Filters.regex("name", "doc-[2-7]"))
          .sort(Sorts.descending("name"))
          .limit(5)
          .all[IO]
        _ <- IO.println(docs)
      } yield ()
    }
}
```

#### Working with case classes

```scala
import cats.effect.{IO, IOApp}
import mongo4cats.client.MongoClientF
import org.mongodb.scala.bson.codecs.Macros._
import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}
import org.mongodb.scala.MongoClient.DEFAULT_CODEC_REGISTRY

import java.time.Instant

object Example extends IOApp.Simple {

  final case class Address(city: String, country: String)
  final case class Person(firstName: String, lastName: String, address: Address, registrationDate: Instant)

  val personCodecRegistry = fromRegistries(
    fromProviders(classOf[Person], classOf[Address]),
    DEFAULT_CODEC_REGISTRY
  )

  val run: IO[Unit] =
    MongoClientF.fromConnectionString[IO]("mongodb://localhost:27017").use { client =>
      for {
        db   <- client.getDatabase("testdb")
        coll <- db.getCollectionWithCodecRegistry[Person]("people", personCodecRegistry)
        _    <- coll.insertOne[IO](Person("John", "Bloggs", Address("New-York", "USA"), Instant.now()))
        docs <- coll.find.stream[IO].compile.toList
        _    <- IO.println(docs)
      } yield ()
    }
}
```
Refer to the official documentation for more sophisticated examples on working with case classes: https://mongodb.github.io/mongo-scala-driver/2.9/bson/macros/

#### Using circe for encoding and decoding documents into case classes

Only mongo4cats-circe is required for basic interop with circe:
```scala
libraryDependencies += "io.github.kirill5k" %% "mongo4cats-circe" % "0.2.4"
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
        coll <- db.getCollectionWithCirceCodecs[Person]("people")
        _    <- coll.insertOne[IO](Person("John", "Bloggs", Address("New-York", "USA"), Instant.now()))
        docs <- coll.find.stream[IO].compile.toList
        _    <- IO.println(docs)
      } yield ()
    }
}
```

