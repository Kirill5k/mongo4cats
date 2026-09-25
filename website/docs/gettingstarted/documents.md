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

`toJson` uses the driver's relaxed output mode. This does not guarantee that every BSON type or numeric width survives a JSON round trip. The `BsonValue` model also cannot represent Decimal128 infinities, NaN, or negative zero.

### JSON integration compatibility

The Circe and ZIO JSON integrations support a subset of [MongoDB Extended JSON](https://www.mongodb.com/docs/manual/reference/mongodb-extended-json/). Their JSON-to-BSON mappers now accept these date and decimal inputs, including inside arrays and documents:

| JSON input | BSON interpretation |
|---|---|
| `{"$date":{"$numberLong":"1640995200123"}}` | Canonical date: signed 64-bit epoch milliseconds |
| `{"$date":"2022-01-01T00:00:00.123Z"}` | ISO instant within the BSON date range |
| `{"$date":1640995200123}` | Legacy numeric date: an exact integer within the signed 64-bit range |
| `{"$date":"2022-01-01"}` | Existing date-only extension: midnight UTC |
| `{"$numberDecimal":"123.4500"}` | A finite Decimal128 value represented by `BigDecimal` |

Date and decimal wrappers must contain exactly the indicated keys. Wrong value types, invalid dates, fractional or overflowing epoch milliseconds, and unrepresentable Decimal128 values are errors. Decimal NaN, infinities, and negative zero are explicitly rejected because `BigDecimal` cannot preserve them. Encoding these malformed wrappers to BSON throws `MongoJsonParsingException`; the public JSON `Document` decoders return a decoding failure instead of letting the exception escape. The `Instant` decoders accept the date forms above and also report invalid inputs as decoding failures.

Existing output formats are preserved: dates are emitted as `{"$date":"<ISO instant>"}`, and decimals as ordinary JSON numbers. Canonical date and decimal wrapper spelling is therefore not preserved, and a numeric BSON type need not survive serialization and reparsing. BSON dates have millisecond precision; finer ISO input precision is lost when written to BSON. ISO date output is retained even outside the years where Extended JSON specifies the relaxed string form. This is not a complete canonical Extended JSON serializer.

This adds a deliberate input interpretation change: a valid `$numberDecimal` wrapper now becomes a BSON decimal instead of an embedded document. Objects using `$date` or `$numberDecimal` alongside other fields are rejected rather than discarding fields. Other special forms, such as standalone `$numberLong`, `$numberInt`, `$numberDouble`, and `$timestamp`, are not added by this change and remain ordinary documents on JSON input. Values rejected by the BSON-to-JSON mapper, such as BSON timestamps, fail explicitly; the ZIO JSON `Document` encoder now throws `MongoJsonParsingException` instead of silently returning `{}`. Undefined document fields continue to be omitted.

### BSON timestamp boundaries

BSON timestamps are distinct from BSON dates. Decoding timestamp seconds now preserves the unsigned 32-bit range, `0` through `4294967295`, in `BTimestamp.seconds: Long`. Values above `2147483647` previously decoded as negative seconds. The increment remains an `Int` carrying its original 32-bit pattern; its API and the timestamp wire format are unchanged. This correction covers Java BSON conversion, document codecs, and change-stream cluster times. Timestamp values are still unsupported by the Circe and ZIO JSON mappers; use the core `Document` JSON reader/writer when working with them.
