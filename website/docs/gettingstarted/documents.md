---
id: documents
title: "Working with documents"
tags: ["Document", "BsonValue"]
---

MongoDB stores data records as BSON documents. BSON is a binary representation of JSON, though it supports more data types — including `ObjectId`, `Instant`/date, `Decimal128`, binary data, and more.

Documents are key-value maps where keys are `String`s and values are `BsonValue`s.

## Creating documents

### Explicit BsonValue wrapping

Every value can be wrapped manually using `BsonValue` factory methods:

```scala
import mongo4cats.bson.{BsonValue, Document, ObjectId}
import java.time.Instant

val doc: Document = Document(
  "_id"            -> BsonValue.objectId(ObjectId.gen),
  "string"         -> BsonValue.string("hello"),
  "int"            -> BsonValue.int(42),
  "long"           -> BsonValue.long(1660999000L),
  "double"         -> BsonValue.double(3.14),
  "boolean"        -> BsonValue.boolean(true),
  "null"           -> BsonValue.Null,
  "dateTime"       -> BsonValue.instant(Instant.now()),
  "array"          -> BsonValue.array(BsonValue.string("a"), BsonValue.string("b")),
  "nested"         -> BsonValue.document(Document("x" -> BsonValue.int(1)))
)
```

### Syntax sugar with `:=`

Import `mongo4cats.bson.syntax._` to use the `:=` operator, which automatically lifts Scala values into `BsonValue`. This is the recommended approach:

```scala
import mongo4cats.bson.{BsonValue, Document, ObjectId}
import mongo4cats.bson.syntax._
import java.time.Instant

val doc: Document = Document(
  "_id"      := ObjectId.gen,
  "string"   := "hello",
  "int"      := 42,
  "long"     := 1660999000L,
  "double"   := 3.14,
  "boolean"  := true,
  "null"     := BsonValue.Null,
  "dateTime" := Instant.now(),
  "array"    := List("a", "b", "c"),
  "nested"   := Document("x" := 1)
)
```

The `:=` syntax supports `String`, `Int`, `Long`, `Double`, `Boolean`, `Instant`, `ObjectId`, `BigDecimal`, `UUID`, `List[A]`, `Option[A]`, nested `Document`, and any type that has an implicit `BsonValueEncoder[A]`.

## Updating documents

`Document` is immutable. Every mutation returns a new document:

```scala
import java.time.Instant

val ts = Instant.now()

// Add a single field (tuple syntax)
val doc2 = doc.add("newField" -> BsonValue.string("value"))

// Add a single field (BsonValueEncoder syntax — preferred)
val doc3 = doc.add("newField" -> "value")

// Alias operators
val doc4 = doc += ("timestamp" -> BsonValue.instant(ts))
val doc5 = doc += ("count"     := 99)

// Remove a field
val doc6 = doc.remove("fieldToRemove")
```

## Reading values

All field values are stored internally as `BsonValue`. Several accessor variants are available:

### Raw BsonValue

```scala
val raw: Option[BsonValue] = doc.get("field")
```

### Typed accessors for common types

```scala
val s: Option[String]           = doc.getString("string")
val i: Option[Int]              = doc.getInt("int")
val l: Option[Long]             = doc.getLong("long")
val d: Option[Double]           = doc.getDouble("double")
val b: Option[Boolean]          = doc.getBoolean("boolean")
val ts: Option[Instant]         = doc.getInstant("dateTime")
val id: Option[ObjectId]        = doc.getObjectId("_id")
val arr: Option[List[BsonValue]] = doc.getList("array")
val nested: Option[Document]    = doc.getDocument("nested")
```

### Generic getAs[T]

`getAs[T]` uses an implicit `BsonValueDecoder[T]` to decode the value. This works for primitives, lists, options, and any type that has a decoder:

```scala
val s:    Option[String]       = doc.getAs[String]("string")
val arr:  Option[List[String]] = doc.getAs[List[String]]("array")
val opt:  Option[Option[Int]]  = doc.getAs[Option[Int]]("maybeInt")
```

### Nested field access

Access fields inside nested documents using dot notation:

```scala
val nested1: Option[BsonValue] = doc.getNested("nested.x")
val nested2: Option[Int]       = doc.getNestedAs[Int]("nested.x")
```

If any segment of the path is absent or not a document, `None` is returned.

### Diagnostic decoding

Use `getAsEither`, `getNestedAsEither`, or `BsonValueDecoder.decode` when you need to explain a failure. They return `Either[BsonErrors, A]`. `Document.getAs` and `getNestedAs` remain convenient `Option` accessors backed by the same decoder.

```scala mdoc:reset
import mongo4cats.bson.{BsonErrors, BsonValue, Document}

val input = Document(
  "values" -> BsonValue.array(
    BsonValue.string("wrong"), BsonValue.int(2), BsonValue.boolean(true)
  )
)

val decoded: Either[BsonErrors, List[Int]] = input.getAsEither[List[Int]]("values")
val paths = decoded.left.map(_.errors.map(_.path))
// Left(Vector(
//   Vector(BsonPathSegment.Field("values"), BsonPathSegment.Index(0)),
//   Vector(BsonPathSegment.Field("values"), BsonPathSegment.Index(2))
// ))
```

`BsonErrors.errors` is a nonempty, ordered `Vector[BsonError]`. Each error has a `kind`, `message`, typed `path`, and optional `expected`, `actual`, and `cause`. Kinds distinguish missing fields, type mismatches, invalid values, unsupported types, decoder failures, and JSON syntax errors. `error.renderPath` formats a path for display, quoting field names when needed. A literal field named `a.b[0]` stays one `Field("a.b[0]")`; use `getAsEither` for such names, since `getNestedAsEither` retains the existing dotted-field syntax.

List decoders collect failures across elements. Compose independent fields with `field` and `zip` to collect their errors together:

```scala mdoc:reset
import mongo4cats.bson.{BsonErrors, BsonValue, BsonValueDecoder}

final case class LineItem(name: String, quantity: Int)

val lineItemDecoder: BsonValueDecoder[LineItem] =
  BsonValueDecoder.field[String]("name")
    .zip(BsonValueDecoder.field[Int]("quantity"))
    .map { case (name, quantity) => LineItem(name, quantity) }

val input = BsonValue.document(
  "name" -> BsonValue.int(123),
  "quantity" -> BsonValue.string("two")
)
val result: Either[BsonErrors, LineItem] = lineItemDecoder.decode(input)
val errorPaths = result.left.map(_.errors.map(_.renderPath))
assert(errorPaths == Left(Vector("$.name", "$.quantity")))
```

A wrong container type produces an error at that container; decoding does not invent child errors below it. Custom decoders implement `decode: BsonValue => Either[BsonErrors, A]`. Use `BsonValueDecoder.fromEither` when you also want nonfatal exceptions captured as structured failures.

### Migrating decoders to 0.8.0

`BsonValueDecoder.decode` now returns `Either[BsonErrors, A]` instead of `Option[A]`. There is one decoder API; call `.toOption` when you do not need the errors. Existing `Document.getAs` and `getNestedAs` calls need no changes.

Update custom decoders to return `Right(value)` on success and `Left(BsonErrors(...))` on failure:

```scala mdoc:reset
import mongo4cats.bson.{BsonError, BsonErrors, BsonValue, BsonValueDecoder}

val decoder: BsonValueDecoder[String] = value =>
  value.asString.toRight(BsonErrors(BsonError(
    BsonError.Kind.TypeMismatch,
    "Expected a BSON string"
  )))

val result: Either[BsonErrors, String] = decoder.decode(BsonValue.string("hello"))
val optional: Option[String] = result.toOption
```

## ObjectId

`ObjectId` is MongoDB's default primary key type. mongo4cats wraps the Java `ObjectId`:

```scala
import mongo4cats.bson.ObjectId

val id: ObjectId = ObjectId.gen          // generate a new ObjectId
val fromStr: ObjectId = ObjectId("507f1f77bcf86cd799439011")
val str: String = id.toHexString
val ts: java.util.Date = id.getDate
```

## JSON serialization

`Document.toJson` and `Document.parse` use the MongoDB driver's JSON writer and reader with mongo4cats BSON codecs:

```scala
val json: String   = doc.toJson
val doc2: Document = Document.parse(json)
```

Extended JSON encodes BSON-specific types (ObjectId, dates, etc.) using standard `$`-prefixed fields:

```json
{
  "_id": { "$oid": "507f1f77bcf86cd799439011" },
  "dateTime": { "$date": "2022-08-20T13:44:45.736Z" }
}
```

The no-argument `toJson` keeps the driver's relaxed output mode. Choose a representation explicitly when exporting BSON:

```scala mdoc:reset
import mongo4cats.bson.{BsonValue, Document, BsonJsonMode}

val exportDoc = Document("count" -> BsonValue.long(1L))
val canonical: String = exportDoc.toJson(BsonJsonMode.Canonical)
val relaxed: String   = exportDoc.toJson(BsonJsonMode.Relaxed)
```

Canonical output preserves supported BSON numeric types with wrappers such as `{"$numberLong":"1"}`. Relaxed output favors ordinary JSON numbers, so BSON numeric widths and some numeric details can be lost when reparsed. These modes preserve BSON values and metadata within the [supported boundaries](#explicit-extended-json), rather than the original JSON spelling or BSON bytes. The explicit overload validates its input and throws `BsonErrors` if a value cannot be represented; use the JSON adapter APIs below when an `Either` result is preferable.

### Explicit Extended JSON

`CirceExtendedJson` in `mongo4cats-circe` and `ZioExtendedJson` in `mongo4cats-zio-json` provide the same opt-in API:

| Method | Result |
|---|---|
| `toBson(jsonAst)` | `Either[BsonErrors, BsonValue]` |
| `fromBson(value, BsonJsonMode.Canonical)` | `Either[BsonErrors, Json]` |
| `fromBson(value, BsonJsonMode.Relaxed)` | `Either[BsonErrors, Json]` |
| `parse(jsonString)` | `Either[BsonErrors, BsonValue]` |

`Document.toJson(mode)` validates its input and then uses the MongoDB driver's JSON writer. `CirceExtendedJson.fromBson` and `ZioExtendedJson.fromBson` use a shared converter to build their respective JSON ASTs. Supported BSON semantics align by mode, subject to the adapter limitations below, but serialized output is not guaranteed to be byte-for-byte identical: formatting and equivalent accepted representations, including UUID wrappers, may differ.

Both readers accept canonical and relaxed Extended JSON for the current BSON model: null, booleans, strings, documents, arrays, Int32, Int64, doubles, finite Decimal128, dates, ObjectIds, binary/UUIDs, timestamps, regular expressions, MinKey, MaxKey, and Undefined. Binary subtypes and regex options survive conversion. Undefined fields are preserved in these explicit APIs. Decimal128 retains its wrapper in both output modes; nonfinite doubles use `$numberDouble` wrappers. Relaxed dates use ISO strings only from 1970 through 9999, with canonical millisecond wrappers outside that range.

The explicit APIs have these boundaries:

- Decimal128 NaN, infinities, and negative zero are rejected because `BsonValue` stores decimals as `BigDecimal`. Doubles support NaN, infinities, and canonical negative zero.
- ZIO's string parser preserves numeric tokens, but an existing `zio.json.ast.Json` may already have lost negative-zero signs or exponent spelling. ZIO relaxed export rejects negative zero with a path-bearing error; canonical output preserves it. See [ZIO numeric handling](../zio.md#explicit-extended-json).
- Dates must fit signed 64-bit milliseconds and have millisecond precision. Finer precision is rejected rather than truncated. Use canonical or ISO date wrappers; the default codecs' numeric-date and date-only extensions are not part of this explicit API.
- Regex patterns must be representable by Scala's `Regex`; unsupported MongoDB-specific patterns fail explicitly. Options must be distinct BSON option letters (`i`, `l`, `m`, `s`, `u`, `x`) and are sorted when decoding and encoding.
- JavaScript, JavaScript-with-scope, Symbol, and DBPointer wrappers are unsupported. Ordinary documents using reserved Extended JSON wrapper keys are rejected by explicit export to avoid ambiguous interpretation.

Malformed wrappers produce structured field/index paths, including wrapper fields such as `$binary.subType`. Independent errors in documents and arrays accumulate in input order. A wrong parent shape prevents decoding its children. Invalid JSON syntax produces one `SyntaxError` with the underlying parser's available message/location; `parse` also rejects trailing non-whitespace. Syntax errors do not promise a BSON field path.

### JSON integration compatibility

The existing implicit Circe and ZIO JSON codecs retain their previous behavior independently of the explicit APIs above. They support a subset of [MongoDB Extended JSON](https://www.mongodb.com/docs/manual/reference/mongodb-extended-json/), accepting these date and decimal inputs inside arrays and documents:

| JSON input | BSON interpretation |
|---|---|
| `{"$date":{"$numberLong":"1640995200123"}}` | Canonical date: signed 64-bit epoch milliseconds |
| `{"$date":"2022-01-01T00:00:00.123Z"}` | ISO instant within the BSON date range |
| `{"$date":1640995200123}` | Legacy numeric date: an exact integer within the signed 64-bit range |
| `{"$date":"2022-01-01"}` | Existing date-only extension: midnight UTC |
| `{"$numberDecimal":"123.4500"}` | A finite Decimal128 value represented by `BigDecimal` |

Date and decimal wrappers must contain exactly the indicated keys. Wrong value types, invalid dates, fractional or overflowing epoch milliseconds, and unrepresentable Decimal128 values are errors. Decimal NaN, infinities, and negative zero are explicitly rejected because `BigDecimal` cannot preserve them. Encoding these malformed wrappers to BSON throws `MongoJsonParsingException`; the public JSON `Document` decoders return a decoding failure instead of letting the exception escape. The `Instant` decoders accept the date forms above and also report invalid inputs as decoding failures.

Existing output formats are preserved: dates are emitted as `{"$date":"<ISO instant>"}`, and decimals as ordinary JSON numbers. Canonical date and decimal wrapper spelling is therefore not preserved, and a numeric BSON type need not survive serialization and reparsing. BSON dates have millisecond precision; finer ISO input precision is lost when written to BSON. ISO date output is retained even outside the years where Extended JSON specifies the relaxed string form. This is not a complete canonical Extended JSON serializer.

A valid `$numberDecimal` wrapper becomes a BSON decimal instead of an embedded document. Objects using `$date` or `$numberDecimal` alongside other fields are rejected rather than discarding fields. Other special forms, such as standalone `$numberLong`, `$numberInt`, `$numberDouble`, and `$timestamp`, remain ordinary documents on input to the default codecs. Values rejected by their BSON-to-JSON mapper, such as BSON timestamps, fail explicitly; the ZIO JSON `Document` encoder throws `MongoJsonParsingException`. Undefined document fields continue to be omitted.

All `BsonValueDecoder`s use the same `decode` method returning `Either[BsonErrors, A]`. Their BSON-to-JSON mapping stage accumulates unsupported values with paths. Circe then retains all failures provided by `Decoder.decodeAccumulating`; custom or monadic Circe decoders may still report one failure. Native ZIO JSON decoders report their first failure, preserving their field/index trace. Neither integration can reconstruct paths already discarded by a custom decoder. The accumulation guarantees of the core `field`/`zip`/list combinators and explicit Extended JSON traversal do not depend on these native-decoder limits.

### BSON timestamp boundaries

BSON timestamps are distinct from BSON dates. Decoding timestamp seconds preserves the unsigned 32-bit range, `0` through `4294967295`, in `BTimestamp.seconds: Long`. The increment remains an `Int` carrying its original 32-bit pattern. This covers Java BSON conversion, document codecs, and change-stream cluster times. Timestamps are supported by the explicit Extended JSON APIs and the core `Document` JSON reader/writer; the default Circe and ZIO JSON codecs retain their existing timestamp limitation.
