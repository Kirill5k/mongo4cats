package mongo4cats.examples

import cats.effect.{IO, IOApp}
import mongo4cats.client.MongoClientF
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.{Filters, Sorts}

object FilteringAndSorting extends IOApp.Simple {

  override val run: IO[Unit] =
    MongoClientF.fromConnectionString[IO]("mongodb://localhost:27017").use { client =>
      for {
        db   <- client.getDatabase("testdb")
        coll <- db.getCollection("docs")
        newDocs = (0 to 10).map(i => Document("name" -> s"doc-$i"))
        _ <- coll.insertMany[IO](newDocs)
        docs <- coll.find
          .filter(Filters.regex("name", "doc-[2-7]"))
          .sort(Sorts.descending("name"))
          .limit(5)
          .all[IO]
        _ <- IO.println(docs)
      } yield ()
    }
}
