---
id: circe
title: Circe
tags: ["Circe", "JSON", "codecs"]
---

The `mongo4cats-circe` module bridges [Circe](https://circe.github.io/circe/) and MongoDB's BSON encoding. It lets you use Circe's automatic codec derivation to read and write Scala case classes directly to and from MongoDB collections, without writing any BSON codec boilerplate.

## Setup

```scala
libraryDependencies += "io.github.kirill5k" %% "mongo4cats-circe" % "<version>"
```

Enable the integration with a single import:

```scala
import mongo4cats.circe._
```

## Special type encodings

mongo4cats-circe registers custom BSON encoders for types that require non-standard JSON representations:

| Scala type | BSON / Extended JSON encoding |
|---|---|
| `org.bson.types.ObjectId` | `{ "$oid": "..." }` |
| `java.time.Instant` | `{ "$date": "..." }` |
| `java.util.UUID` | `{ "$binary": { ... } }` |
| `BigDecimal` | `{ "$numberDecimal": "..." }` |

These encodings are compatible with MongoDB Extended JSON so that `doc.toJson` and `Document.fromJson` round-trip correctly.

## Reading and writing BSON values

With `Encoder[T]` and `Decoder[T]` in scope (e.g. via `io.circe.generic.auto._`), a value of type `T` can be converted to/from `BsonValue` directly:

```scala
import io.circe.generic.auto._
import mongo4cats.bson.{Document, ObjectId}
import mongo4cats.bson.syntax._
import mongo4cats.circe._
import java.time.Instant

final case class User(
  _id: ObjectId,
  name: String,
  email: String,
  createdAt: Instant,
  tags: List[String],
  score: Option[Double]
)

val user = User(
  _id       = ObjectId.gen,
  name      = "Alice",
  email     = "alice@example.com",
  createdAt = Instant.now(),
  tags      = List("admin", "user"),
  score     = Some(9.5)
)

// Embed the case class inside a Document field
val doc = Document(
  "_id"  := ObjectId.gen,
  "user" := user
)

// Retrieve it back
val retrieved: Option[User] = doc.getAs[User]("user")
// Some(User(...))
```

## Typed collections

To store and retrieve a case class as the collection's document type, derive a `MongoCodecProvider[T]` and use `getCollectionWithCodec`:

### Automatic derivation (recommended)

```scala
import io.circe.generic.auto._
import mongo4cats.codecs.MongoCodecProvider
import mongo4cats.circe._

// Place this in the companion object so it is always in scope
object User {
  implicit val codec: MongoCodecProvider[User] = deriveCirceCodecProvider[User]
}
```

```scala
import cats.effect.IO
import mongo4cats.collection.MongoCollection

// The implicit MongoCodecProvider[User] is found automatically
val collection: IO[MongoCollection[IO, User]] =
  database.getCollectionWithCodec[User]("users")
```

### Full example with insert and find

```scala
import cats.effect.{IO, IOApp}
import io.circe.generic.auto._
import mongo4cats.bson.ObjectId
import mongo4cats.circe._
import mongo4cats.client.MongoClient
import mongo4cats.codecs.MongoCodecProvider
import mongo4cats.operations.Filter
import java.time.Instant

final case class User(
  _id: ObjectId,
  name: String,
  email: String,
  createdAt: Instant
)

object User {
  implicit val codec: MongoCodecProvider[User] = deriveCirceCodecProvider[User]
}

object CirceExample extends IOApp.Simple {
  override val run: IO[Unit] =
    MongoClient.fromConnectionString[IO]("mongodb://localhost:27017").use { client =>
      for {
        db   <- client.getDatabase("mydb")
        coll <- db.getCollectionWithCodec[User]("users")
        _    <- coll.insertOne(User(ObjectId.gen, "Alice", "alice@example.com", Instant.now()))
        users <- coll.find(Filter.eq("name", "Alice")).all
        _    <- IO.println(s"Found: $users")
      } yield ()
    }
}
```

## Custom Circe encoders

You can provide your own `Encoder`/`Decoder` instances instead of relying on auto-derivation:

```scala
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto._

final case class Product(id: String, price: BigDecimal)

object Product {
  implicit val encoder: Encoder[Product] = deriveEncoder[Product]
  implicit val decoder: Decoder[Product] = deriveDecoder[Product]
  implicit val codec: MongoCodecProvider[Product] = deriveCirceCodecProvider[Product]
}
```

## Nested case classes

Nested case classes are handled automatically as long as each type has a Circe `Encoder`/`Decoder` in scope:

```scala
final case class Address(street: String, city: String)
final case class Person(name: String, address: Address)

// With io.circe.generic.auto._ all three types (Address, Person, MongoCodecProvider[Person]) are derived
```

## Handling ObjectId fields

When the `_id` field is typed as `ObjectId`, mongo4cats-circe handles encoding to the `$oid` Extended JSON format automatically. If you prefer to use `String` for the id in your domain model, you can add a custom encoder/decoder:

```scala
import io.circe.{Decoder, Encoder}
import org.bson.types.ObjectId

implicit val objectIdEncoder: Encoder[ObjectId] = Encoder[String].contramap(_.toHexString)
implicit val objectIdDecoder: Decoder[ObjectId] = Decoder[String].map(new ObjectId(_))
```