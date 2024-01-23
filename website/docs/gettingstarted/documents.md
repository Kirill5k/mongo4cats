---
id: document
title:  "Working with documents"
tags: ["Document"]
---

## Working with documents

MongoDB stores data records as BSON documents. BSON is a binary representation of JSON objects, though it contains more data types than JSON. 

Documents are composed of key-and-value pairs, where keys are represented as regular strings and values can be any of the BSON data types, including other documents, arrays, and arrays of documents.

### Creating new documents

A document can be created by using one of the available constructor methods in `Document` companion object:

```scala
import mongo4cats.bson.{BsonValue, Document, ObjectId}
import java.time.Instant

val doc: Document = Document(
  "_id"            -> BsonValue.objectId(ObjectId.gen),
  "null"           -> BsonValue.Null,
  "string"         -> BsonValue.string("str"),
  "int"            -> BsonValue.int(1),
  "boolean"        -> BsonValue.boolean(true),
  "double"         -> BsonValue.double(2.0),
  "int"            -> BsonValue.int(1),
  "long"           -> BsonValue.long(1660999000L),
  "dateTime"       -> BsonValue.instant(Instant.now),
  "array"          -> BsonValue.array(BsonValue.string("item1"), BsonValue.string("item2"), BsonValue.string("item3")),
  "nestedDocument" -> BsonValue.document(Document("field" -> BsonValue.string("nested")))
)
```

To avoid having a need for wrapping every value in `BsonValue`, additional syntax can be imported from `mongo4cats.bson.syntax` package, which will enable automatic conversion of key-value pairs into bson with `:=` method:

```scala
import mongo4cats.bson.syntax._

val doc: Document = Document(
  "_id"            := ObjectId.gen,
  "null"           := BsonValue.Null,
  "string"         := "str",
  "int"            := 1,
  "boolean"        := true,
  "double"         := 2.0,
  "int"            := 1,
  "long"           := 1660999000L,
  "dateTime"       := Instant.now,
  "array"          := List("item1", "item2", "item3"),
  "nestedDocument" := Document("field" := "nested")
)
```

### Updating documents

Documents are immutable in nature, therefore adding a new value to a document will result in creation of a new document. There are several methods available for updating documents:

```scala
val updatedDoc1 = doc.add("newField" -> BsonValue.string("string"))
val updatedDoc2 = doc.add("newField" -> "string")
val updatedDoc3 = doc += ("anotherNewField" -> BsonValue.instant(ts))
val updatedDoc4 = doc += ("anotherNewField" := 1)
```

### Retrieving values

Internally, all field values are stored as `BsonValue`. To retrieve a value by key, several variations of get methods exist:

```scala
val stringField1: Option[BsonValue] = doc.get("string")
val stringField2: Option[String]    = doc.getString("string")
val stringField3: Option[String]    = doc.getAs[String]("string")

val arrayField1: Option[BsonValue]       = doc.get("array")
val arrayField2: Option[List[BsonValue]] = doc.getList("array")
val arrayField3: Option[List[String]]    = doc.getAs[List[String]]("array")

val nestedField1: Option[BsonValue] = doc.getNested("nestedDocument.field")
val nestedField2: Option[String]    = doc.getNestedAs[String]("nestedDocument.field")
```

If a requested value is not present in the document, an empty option will be returned.


