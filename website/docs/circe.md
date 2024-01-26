---
id: circe
title: Circe
tags: ["Circe", "JSON"]
---

Given that MongoDB stores data records as BSON documents, which bear a lot of similarities to a traditional JSON objects, [Circe](https://circe.github.io/circe/) (and other JSON libraries) can be used for deriving codecs for converting Scala case class into documents.

To enable Circe support, a dependency has to be added in the `build.sbt`:
```scala
libraryDependencies += "io.github.kirill5k" %% "mongo4cats-circe" % "<version>"
```
Once the dependency is in, automatic derivation of MongoDB codecs can be enabled by including the following import:
```scala
import mongo4cats.circe._
```

`mongo4cats.circe` includes several functions for deriving bson value encoders and decoders from provided Circe codecs, as well codecs for converting some special data types (ObjectId and dates) to MongoDB's specific bson representations.

### JSON to BSON conversions

Assuming there are instances of `Encoder[T]` and `Decoder[T]` available in the implicit scope, a class `T` can be converted to a bson value and back:

```scala
import io.circe.generic.auto._
import mongo4cats.bson.{Document, ObjectId}
import mongo4cats.circe._
import mongo4cats.bson.syntax._

import java.time.Instant

final case class MyClass(
  _id: ObjectId,
  dateField: Instant,
  stringField: String,
  intField: Int,
  longField: Long,
  arrayField: List[String],
  optionField: Option[String]
)

val myClass = MyClass(
  _id = ObjectId.gen,
  dateField = Instant.now(),
  stringField = "string",
  intField = 1,
  longField = 1660999000L,
  arrayField = List("item1", "item2"),
  optionField = None
)

val doc = Document("_id" := ObjectId.gen, "myClasses" := List(myClass))
val jsonString = doc.toJson
//{
//  "_id": {
//    "$oid": "6300e54d64332103430291d3"
//  },
//  "myClasses": [
//  {
//    "_id": {
//      "$oid": "6300e54d64332103430291d2"
//    },
//    "dateField": {
//      "$date": "2022-08-20T13:44:45.736Z"
//    },
//    "stringField": "string",
//    "intField": 1,
//    "longField": 1660999000,
//    "arrayField": [
//    "item1",
//    "item2"
//    ],
//    "optionField": null
//  }
//  ]
//}
val retrievedMyClasses = doc.getAs[List[MyClass]]("myClasses")
//Some(List(MyClass(6300e54d64332103430291d2,2022-08-20T13:44:45.736633Z,string,1,1660999000,List(item1, item2),None)))
```

### Deriving codecs for collections

In order to be able to derive codecs for case classes and use them with collections, we need to build an instance of `MongoCodecProvider[T]`. 
This can be done automatically on the fly or manually by creating codec provider with `deriveCirceCodecProvider` function, assuming there are instances of `Encoder[T]` and `Decoder[T]` available in the implicit scope:

```scala
import io.circe.generic.auto._
import mongo4cats.codecs.MongoCodecProvider
import mongo4cats.circe._

object MyClass {
  implicit val myClassCodecProvider: MongoCodecProvider[MyClass] = deriveCirceCodecProvider
}
```

To use it with `MongoCollection`, codec provider needs to be added to the codec registry:

```scala
import cats.effect.IO
import mongo4cats.collection.MongoCollection

val collection: IO[MongoCollection[IO, MyClass]] = database.getCollectionWithCodec[MyClass]("mycoll")
```