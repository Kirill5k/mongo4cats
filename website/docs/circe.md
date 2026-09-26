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

| Scala type | JSON output |
|---|---|
| `org.bson.types.ObjectId` | `{ "$oid": "..." }` |
| `java.time.Instant` | `{ "$date": "..." }` |
| `java.util.UUID` | `{ "$binary": { ... } }` |
| `BigDecimal` | An ordinary JSON number |

The integration accepts canonical date and finite `$numberDecimal` wrappers on input while preserving these output formats. See [JSON integration compatibility](gettingstarted/documents.md#json-integration-compatibility) for supported forms, decoding errors, and round-trip limitations. The core `doc.toJson` / `Document.parse(json)` path uses the driver's JSON reader/writer and is separate from Circe conversion.

## Explicit Extended JSON

Use `CirceExtendedJson` to exchange BSON as canonical or relaxed Extended JSON. It is independent of the implicit codecs used for domain objects:

```scala mdoc:reset
import io.circe.Json
import mongo4cats.bson.{BsonErrors, BsonValue, BsonJsonMode}
import mongo4cats.circe.CirceExtendedJson

val value = BsonValue.document(
  "count" -> BsonValue.long(1L),
  "pattern" -> BsonValue.regex("a.*".r, "im")
)

val json: Either[BsonErrors, Json] =
  CirceExtendedJson.fromBson(value, BsonJsonMode.Canonical)
val restored: Either[BsonErrors, BsonValue] = json.flatMap(CirceExtendedJson.toBson)
val parsed: Either[BsonErrors, BsonValue] =
  CirceExtendedJson.parse("""{"count":{"$numberLong":"1"}}""")
assert(restored.map(_.asJava) == Right(value.asJava))
```

Canonical output preserves supported BSON types, including numeric widths, binary subtype, and regex options. Choose `BsonJsonMode.Relaxed` for more ordinary JSON numbers, accepting their numeric round-trip limitations. Both input representations are accepted. `toBson` and `parse` accumulate semantic errors with typed field/index paths; invalid JSON syntax returns a single `SyntaxError`, including the parser's available location. `parse` rejects duplicate object keys before Circe can discard them. `toBson` cannot recover duplicate keys already removed while building an AST. See [shared output semantics and supported boundaries](gettingstarted/documents.md#explicit-extended-json) for differences from `Document.toJson(mode)` and the Decimal128, regex, date precision, and reserved-key constraints.

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

// Parse the document's Extended JSON representation
val parsed: Document = Document.parse(doc.toJson)
```

### Diagnostic domain decoding

Use `getAsEither` to keep decoding errors, or call `decode` on a derived BSON decoder:

```scala mdoc:reset
import io.circe.generic.auto._
import mongo4cats.bson.{BsonErrors, BsonValue, BsonValueDecoder, Document}
import mongo4cats.circe._

final case class User(name: String, tags: List[String])

val input = BsonValue.document(
  "name" -> BsonValue.int(123),
  "tags" -> BsonValue.array(BsonValue.string("admin"), BsonValue.string("staff"), BsonValue.int(2))
)
val doc = Document("user" -> input)
val detailed: Either[BsonErrors, User] = doc.getAsEither[User]("user")
val decoder: BsonValueDecoder[User] = deriveJsonBsonValueDecoder[User]
val direct: Either[BsonErrors, User] = decoder.decode(input)
assert(detailed.left.map(_.errors.map(_.renderPath)) ==
  Left(Vector("$.user.name", "$.user.tags[2]")))
```

Failures include typed paths such as `Vector(Field("user"), Field("tags"), Index(2))`. `error.renderPath` produces a readable path while preserving literal dots or brackets in field names. BSON-to-JSON mapping failures accumulate before domain decoding. The domain stage calls Circe's `decodeAccumulating`, retaining all failures exposed by that decoder; custom decoders and monadic dependencies can still report only their first failure. `getAs` returns `Option`; `decode` returns `Either[BsonErrors, A]`. Use `decoder.decode(value).toOption` when you do not need the errors.

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
