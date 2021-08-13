---
layout: docs
title:  "Distinct"
number: 5
---

## Distinct

Distinct operation returns all distinct values for a field across all documents in a collection.
The operation can be executed by calling `distinct` method on a `MongoCollectionF[T]` class and passing a name of a field:
```scala
val distinctValues: IO[Iterable[String]] = collection.distinct[String]("field1").all[IO]

// or stream all found values instead
val distinctValues: fs2.Stream[IO, String] = collection.distinct[String]("field1").stream[IO]
```
If the document field is represented by a more complicated class in a collection than a String, it can be upcasted to a required type:
```scala
val distinctValues: IO[Iterable[Document]] = collection.distinct[Document]("field1").all[IO]

// assuming you have an instance of MongoCodecProvider[MyClass] available in the implicit scope
val distinctValues: IO[Iterable[MyClass]] = collection.distinctWithCodec[MyClass]("field1").all[IO]

// or you can add codecs explicitly
val distinctValues: IO[Iterable[MyClass]] = collection.withAddedCodec(myClassCodecs).distinct[MyClass]("field1").all[IO]
```