---
id: connection
title:  "Making a connection"
tags: ["MongoClient", "MongoDatabase"]
---

## Making a connection

In order to create a connection to a MongoDB database, an instance of `MongoClient[F]` class needs to be instantiated. 
The `MongoClient[F]` instance represents a pool of connections for a given MongoDB server deployment and typically
only one instance of this class is required per application (even with multiple operations executed concurrently).

There are multiple ways of creating a client:

```scala
import cats.effect.IO
import mongo4cats.models.client._
import mongo4cats.client._

// From a connection string
val clientFromConnString = MongoClient.fromConnectionString[IO]("mongodb://localhost:27017")

// By providing ServerAddress
val clientFromServerAddress = MongoClient.fromServerAddress[IO](ServerAddress("localhost", 27017))

// By providing Connection
val connection = MongoConnection("localhost", 27017, Some(MongoCredential("username", "password")), MongoConnectionType.Classic)
val clientFromConnection = MongoClient.fromConnection[IO](connection)

// By providing custom MongoClientSettings object
val settings = MongoClientSettings.builder().applyConnectionString(ConnectionString("mongodb://localhost:27017")).build()
val clientFromSettings = MongoClient.create[IO](settings)
```

Creating a client through any of the available constructor methods in its companion object returns a `Resource[F, MongoClient[F]]`, meaning that the connection to the MongoDB server will be disposed after its use.

Once the client is created, it can further be used for interacting with `MongoDatabase[F]` instances that provide methods for dealing with your actual MongoDB database:

```scala
import mongo4cats.database.MongoDatabase

MongoClient.fromConnectionString[IO]("mongodb://localhost:27017").use { client =>
  val database: IO[MongoDatabase[IO]] = client.getDatabase("mydb")
}
```

If the database does not exist, MongoDB will create it during the very first query to it.
