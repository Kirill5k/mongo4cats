---
id: zio
title: ZIO
tags: ["ZIO", "ZIO 2"]
---

The `mongo4cats-zio` module provides `ZMongoClient`, `ZMongoDatabase`, and `ZMongoCollection` — type aliases that use `Task` for operations and `ZStream` for streaming results. Client and session acquisition use ZIO's `Scope` for resource management.

## Setup

```scala
// Core ZIO integration
libraryDependencies += "io.github.kirill5k" %% "mongo4cats-zio" % "<version>"

// Embedded MongoDB for tests
libraryDependencies += "io.github.kirill5k" %% "mongo4cats-zio-embedded" % "<version>" % Test
```

Import everything from:

```scala
import mongo4cats.zio._
```

## Type aliases

| Alias | Expands to |
|---|---|
| `ZMongoClient` | `GenericMongoClient[Task, ZStream[Any, Throwable, *], RIO[Scope, *]]` |
| `ZMongoDatabase` | `GenericMongoDatabase[Task, ZStream[Any, Throwable, *]]` |
| `ZMongoCollection[T]` | `GenericMongoCollection[Task, T, ZStream[Any, Throwable, *]]` |

## Connecting to MongoDB

`ZMongoClient.fromConnectionString` returns a `ZIO[Scope, Throwable, ZMongoClient]`, making it easy to wire it into the ZIO layer system:

```scala
import mongo4cats.bson.Document
import mongo4cats.zio._
import zio._

// As ZLayers for dependency injection
val clientLayer: ZLayer[Any, Throwable, ZMongoClient] =
  ZLayer.scoped(ZMongoClient.fromConnectionString("mongodb://localhost:27017"))

val dbLayer: ZLayer[ZMongoClient, Throwable, ZMongoDatabase] =
  ZLayer.fromZIO(ZIO.serviceWithZIO[ZMongoClient](_.getDatabase("my-db")))

val collectionLayer: ZLayer[ZMongoDatabase, Throwable, ZMongoCollection[Document]] =
  ZLayer.fromZIO(ZIO.serviceWithZIO[ZMongoDatabase](_.getCollection("docs")))
```

## Basic CRUD example

```scala
import mongo4cats.bson.Document
import mongo4cats.bson.syntax._
import mongo4cats.bson.ObjectId
import mongo4cats.operations.Filter
import mongo4cats.zio._
import zio._

object ZioExample extends ZIOAppDefault {
  override val run: Task[Unit] =
    ZIO.scoped {
      ZMongoClient.fromConnectionString("mongodb://localhost:27017").flatMap { client =>
        for {
          db   <- client.getDatabase("mydb")
          coll <- db.getCollection("users")
          _    <- coll.insertMany(List(
                    Document("name" := "Alice", "score" := 95),
                    Document("name" := "Bob",   "score" := 70)
                  ))
          docs <- coll.find(Filter.gte("score", 80)).all
          _    <- ZIO.foreach(docs)(d => Console.printLine(d.toString))
        } yield ()
      }
    }
}
```

## Streaming

Results are returned as `ZStream` when calling `.stream`:

```scala
import zio.stream.ZStream

val stream: ZStream[Any, Throwable, Document] =
  collection.find(Filter.gte("score", 50)).stream

val printDocuments: Task[Unit] =
  stream.runForeach(doc => Console.printLine(doc.toString))
```

## ZIO JSON integration

Use `mongo4cats-zio-json` for codec derivation with [ZIO JSON](https://zio.github.io/zio-json/) instead of Circe:

```scala
libraryDependencies += "io.github.kirill5k" %% "mongo4cats-zio-json" % "<version>"
```

```scala
import mongo4cats.codecs.MongoCodecProvider
import mongo4cats.zio.json._
import zio.json._

final case class User(name: String, score: Int)

object User {
  implicit val codec: JsonCodec[User] = DeriveJsonCodec.gen[User]
  implicit val mongoCodec: MongoCodecProvider[User] = deriveZioJsonCodecProvider[User]
}

val coll: Task[ZMongoCollection[User]] = db.getCollectionWithCodec[User]("users")
```

The integration accepts canonical dates and finite `$numberDecimal` wrappers, with the same [JSON compatibility and error behavior](gettingstarted/documents.md#json-integration-compatibility) as Circe. Output remains ISO date wrappers and plain decimal numbers; not all Extended JSON forms or BSON types can round-trip through these codecs.

### Explicit Extended JSON

Use `ZioExtendedJson` for canonical or relaxed BSON interchange without changing the implicit domain codecs:

```scala mdoc:reset
import mongo4cats.bson.{BsonErrors, BsonValue, BsonJsonMode}
import mongo4cats.zio.json.ZioExtendedJson
import zio.json.ast.Json

val value = BsonValue.document(
  "count" -> BsonValue.long(1L),
  "pattern" -> BsonValue.regex("a.*".r, "im")
)

val json: Either[BsonErrors, Json] =
  ZioExtendedJson.fromBson(value, BsonJsonMode.Canonical)
val restored: Either[BsonErrors, BsonValue] = json.flatMap(ZioExtendedJson.toBson)
val parsed: Either[BsonErrors, BsonValue] =
  ZioExtendedJson.parse("""{"count":{"$numberLong":"1"}}""")
assert(restored.map(_.asJava) == Right(value.asJava))
```

Canonical output preserves supported BSON types and metadata. `BsonJsonMode.Relaxed` favors ordinary JSON numbers and can lose numeric type information when reparsed. Both readers accept canonical and relaxed forms, accumulating independent wrapper errors with typed field/index paths. Invalid JSON syntax, including trailing non-whitespace after a value, returns one `SyntaxError`. See [shared output semantics and supported boundaries](gettingstarted/documents.md#explicit-extended-json) for differences from `Document.toJson(mode)` and the Decimal128, regex, date precision, and reserved-key constraints.

`ZioExtendedJson.parse(String)` preserves original numeric tokens, including the sign of `-0.0` and the exponent in `1e0`, after the native parser validates the input. `toBson(existingJson)` cannot recover numeric information already erased by ZIO's `Json.Num` representation. Relaxed export of BSON double negative zero therefore returns an `InvalidValue` error at its field/index path; choose canonical output, whose `$numberDouble` string preserves the sign.

### Diagnostic domain decoding

Derived BSON decoders use a single `decode` method returning `Either[BsonErrors, A]`:

```scala mdoc:reset
import mongo4cats.bson.{BsonErrors, BsonValue, BsonValueDecoder}
import mongo4cats.zio.json._
import zio.json.{DeriveJsonDecoder, JsonDecoder}

final case class User(name: String, score: Int)
implicit val userDecoder: JsonDecoder[User] = DeriveJsonDecoder.gen[User]

val decoder: BsonValueDecoder[User] = deriveJsonBsonValueDecoder[User]
val input = BsonValue.document(
  "name" -> BsonValue.string("Alice"),
  "score" -> BsonValue.string("wrong")
)

val detailed: Either[BsonErrors, User] = decoder.decode(input)
// The error path is Vector(BsonPathSegment.Field("score")).
val convenient: Option[User] = decoder.decode(input).toOption
assert(detailed.left.map(_.head.renderPath) == Left("$.score"))
assert(convenient.isEmpty)
```

`Document.getAsEither` and `getNestedAsEither` add the enclosing document fields to those paths. BSON-to-JSON mapping failures accumulate across documents and arrays. Native ZIO JSON decoders return their first domain-decoding failure; the integration preserves its typed field/index trace, including punctuation in field names. Core BSON `field`/`zip`/list decoders and explicit Extended JSON validation can accumulate independent failures. Custom decoders that discard their trace cannot provide the discarded path.

## Transactions

```scala
import mongo4cats.zio._

ZIO.scoped {
  ZMongoClient.fromConnectionString("mongodb://localhost:27017/?retryWrites=false").flatMap { client =>
    for {
      db   <- client.getDatabase("mydb")
      coll <- db.getCollection("docs")
      _ <- client.startSession.flatMap { session =>
        for {
          _ <- session.startTransaction
          _ <- coll.insertOne(session, Document("name" := "test"))
          _ <- session.commitTransaction
        } yield ()
      }
    } yield ()
  }
}
```

## Embedded MongoDB for tests

```scala
import mongo4cats.zio.embedded._
```

Extend `EmbeddedMongo` in your test suite to start a temporary MongoDB instance:

```scala
import mongo4cats.bson._
import mongo4cats.bson.syntax._
import mongo4cats.zio._
import mongo4cats.zio.embedded.EmbeddedMongo
import zio._
import zio.test._
import zio.test.Assertion._

object ZMongoCollectionSpec extends ZIOSpecDefault with EmbeddedMongo {

  override def spec = suite("ZMongoCollection")(
    test("inserts and retrieves documents") {
      withRunningEmbeddedMongo(27017) {
        ZIO
          .serviceWithZIO[ZMongoDatabase] { db =>
            for {
              coll   <- db.getCollection("coll")
              doc     = Document("_id" := ObjectId.gen, "value" := 42)
              _      <- coll.insertOne(doc)
              result <- coll.find.all
            } yield assert(result)(equalTo(List(doc)))
          }
          .provide(
            ZLayer.scoped[Any](ZMongoClient.fromConnectionString("mongodb://localhost:27017")),
            ZLayer.fromZIO(ZIO.serviceWithZIO[ZMongoClient](_.getDatabase("testdb")))
          )
      }
    }
  ) @@ TestAspect.sequential @@ TestAspect.withLiveClock @@ TestAspect.timeout(2.minutes)
}
```

The `withRunningEmbeddedMongo` method starts an embedded MongoDB instance, executes the provided ZIO effect, then shuts the instance down. You can override `mongoPort` (default 27017) at the class level or pass the port explicitly. Connect to `localhost` using that port; there is no host/port overload. The live-clock aspect lets startup retries advance, and the timeout bounds failed tests.
