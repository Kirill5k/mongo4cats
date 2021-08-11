---
layout: docs
title:  "Making a connection"
number: 2
---

## Making a connection

In order to create a connection to a MongoDB database, an instance of `MongoClientF` class needs to be instantiated. 
The `MongoClientF` instance represents a pool of connections for a given MongoDB server deployment and typically
only one instance of this class is required per application (even with multiple operations executed concurrently).

There are multiple ways of creating a client:

```scala
import cats.effect.IO
import mongo4cats.client._

// From a connection string
val client = MongoClientF.fromConnectionString[IO]("mongodb://localhost:27017")

// By providing ServerAddress object
val client = MongoClientF.fromServerAddress[IO](ServerAddress("localhost", 27017))

// By providing custom MongoClientSettings object
val settings = MongoClientSettings.builder.build()
val client = MongoClientF.create[IO](settings)
```

Creating a client through any of the available constructor methods in its companion object returns a `Resource[IO, MongoClientF[IO]]`, meaning that the connection to the MongoDB server will be disposed after its use.

Once the client is created, it can further be used for interacting with `MongoDatabaseF` instances that provide methods for dealing with your actual MongoDB database:

```scala
MongoClientF.fromConnectionString[IO]("mongodb://localhost:27017").use { client =>
  for {
    database <- client.getDatabase("mydb")
  } yield ()
}
```

If the database does not exist, MongoDB will create it during the very first query to it.