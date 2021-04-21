package mongo4cats.examples

import cats.effect.{IO, IOApp}
import io.circe.generic.auto._
import mongo4cats.client.MongoClientF
import mongo4cats.circe._

object CaseClassesWithCirceCodecs extends IOApp.Simple {

  final case class Address(city: String, country: String)
  final case class Person(firstName: String, lastName: String, address: Address)

  override val run: IO[Unit] =
    MongoClientF.fromConnectionString[IO]("mongodb://localhost:27017").use { client =>
      for {
        db   <- client.getDatabase("people")
        coll <- db.getCollectionWithCirceCodecs[Person]("collection")
        _    <- coll.insertOne[IO](Person("John", "Bloggs", Address("New-York", "USA")))
        docs <- coll.find.stream[IO].compile.toList
        _    <- IO.println(docs)
      } yield ()
    }
}
