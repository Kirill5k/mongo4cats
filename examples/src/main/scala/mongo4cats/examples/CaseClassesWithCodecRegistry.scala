package mongo4cats.examples

import cats.effect.{IO, IOApp}
import mongo4cats.client.MongoClientF
import org.mongodb.scala.bson.codecs.Macros._
import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}
import org.mongodb.scala.MongoClient.DEFAULT_CODEC_REGISTRY

object CaseClassesWithCodecRegistry extends IOApp.Simple {

  final case class Address(city: String, country: String)
  final case class Person(firstName: String, lastName: String, address: Address)

  val personCodecRegistry = fromRegistries(
    fromProviders(classOf[Person], classOf[Address]),
    DEFAULT_CODEC_REGISTRY
  )

  override val run: IO[Unit] =
    MongoClientF.fromConnectionString[IO]("mongodb://localhost:27017").use { client =>
      for {
        db   <- client.getDatabase("people")
        coll <- db.getCollectionWithCodecRegistry[Person]("collection", personCodecRegistry)
        _    <- coll.insertOne[IO](Person("John", "Bloggs", Address("New-York", "USA")))
        docs <- coll.find.stream[IO].compile.toList
        _    <- IO.println(docs)
      } yield ()
    }
}
