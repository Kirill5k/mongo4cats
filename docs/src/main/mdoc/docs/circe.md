---
layout: docs
title: Circe
number: 3
position: 3
---

## Circe

Internally, MongoDB stores all of its data in a BSON (Binary JSON) format, which is a close cousin of a traditional JSON that we all got used to.
Similarities between these two formats give us ability to use tools that are normally used for doing conversions of case classes into a JSON for deriving MongoDB codecs. One of such tools is [circe](https://circe.github.io/circe/).

To enable circe support, a dependency has to be added in the `build.sbt`:
```scala
libraryDependencies += "io.github.kirill5k" %% "mongo4cats-circe" % "<version>"
```
Once the dependency is in, automatic derivation of MongoDB codecs can be enabled by including the following import:
```scala
import mongo4cats.circe._
```
Which, when included, implicitly builds an instance of `MongoCodecProvider[T]`, 
assuming there are instances of `Encoder[T]` and `Decoder[T]` available in the implicit scope:
```scala
import io.circe.generic.auto._
import mongo4cats.bson.ObjectId
import mongo4cats.circe._
import mongo4cats.database.MongoCollectionF

final case class MyClass(_id: ObjectId, field1: String, field2: Int)

val collection: IO[MongoCollectionF[MyClass]] = database.getCollectionWithCodec[MyClass]("mycoll")
```