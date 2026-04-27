---
id: connection
title: "Making a connection"
tags: ["MongoClient", "MongoDatabase"]
---

## MongoClient

`MongoClient[F]` is the entry point to the library. It represents a managed pool of connections to a MongoDB deployment. Only one instance is typically needed per application — it is safe to share across concurrent operations.

All constructors return `Resource[F, MongoClient[F]]`, so the connection pool is automatically closed when the resource scope exits.

```scala
import cats.effect.IO
import mongo4cats.client.MongoClient
import mongo4cats.models.client._

// Most common: from a connection string
val client1 = MongoClient.fromConnectionString[IO]("mongodb://localhost:27017")

// From a host/port pair
val client2 = MongoClient.fromServerAddress[IO](ServerAddress("localhost", 27017))

// With credentials
val connection = MongoConnection(
  host = "localhost",
  port = 27017,
  credential = Some(MongoCredential("username", "password")),
  connectionType = MongoConnectionType.Classic
)
val client3 = MongoClient.fromConnection[IO](connection)

// Full control via the standard MongoDB Java driver settings object
import com.mongodb.{ConnectionString, MongoClientSettings}
val settings = MongoClientSettings.builder()
  .applyConnectionString(ConnectionString("mongodb://localhost:27017"))
  .build()
val client4 = MongoClient.create[IO](settings)
```

### Replica sets and Atlas

Connection strings for replica sets and Atlas are passed through exactly as defined by the MongoDB driver:

```scala
// Replica set
val rsClient = MongoClient.fromConnectionString[IO](
  "mongodb://host1:27017,host2:27017,host3:27017/?replicaSet=myReplicaSet"
)

// MongoDB Atlas
val atlasClient = MongoClient.fromConnectionString[IO](
  "mongodb+srv://username:password@cluster0.example.mongodb.net/?retryWrites=true&w=majority"
)
```

### Using the client

Wrap your program inside `use` to ensure the connection pool is released when work is done:

```scala
MongoClient.fromConnectionString[IO]("mongodb://localhost:27017").use { client =>
  for {
    names <- client.listDatabaseNames
    _     <- IO.println(s"Databases: $names")
  } yield ()
}
```

Additional client-level operations:

```scala
// List all databases with metadata (size, name, etc.)
val dbs: IO[Iterable[Document]] = client.listDatabases

// Get the cluster topology description
val desc = client.clusterDescription
```

## MongoDatabase

Obtain a `MongoDatabase[F]` by name from the client. If the database does not yet exist, MongoDB creates it on the first write.

```scala
import mongo4cats.database.MongoDatabase

MongoClient.fromConnectionString[IO]("mongodb://localhost:27017").use { client =>
  for {
    db    <- client.getDatabase("mydb")
    names <- db.listCollectionNames
    _     <- IO.println(s"Collections: $names")
  } yield ()
}
```

Key `MongoDatabase[F]` methods:

| Method | Description |
|---|---|
| `getCollection(name)` | Get a `MongoCollection[F, Document]` |
| `getCollection[T](name, registry)` | Get a typed collection with an explicit codec registry |
| `getCollectionWithCodec[T](name)` | Get a typed collection using an implicit `MongoCodecProvider[T]` |
| `createCollection(name)` | Explicitly create a collection |
| `createCollection(name, options)` | Create a collection with options (e.g. capped, time series) |
| `listCollectionNames` | List all collection names in the database |
| `runCommand(command)` | Execute a raw database command |
| `drop` | Drop the entire database |

### Creating a capped collection

```scala
import mongo4cats.models.database.CreateCollectionOptions

val options = CreateCollectionOptions().capped(true).sizeInBytes(1024L * 1024L)
db.createCollection("events", options)
```
