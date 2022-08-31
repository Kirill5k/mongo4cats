/*
 * Copyright 2020 Kirill5k
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package mongo4cats.examples

import cats.effect.{IO, IOApp}
import io.circe.generic.auto._
import mongo4cats.circe._
import mongo4cats.client.MongoClient
import mongo4cats.embedded.EmbeddedMongo

import java.time.Instant

object DistinctNestedClassesWithCirceCodecs extends IOApp.Simple with EmbeddedMongo {

  final case class Address(city: String, country: String)
  final case class Person(firstName: String, lastName: String, address: Address, registrationDate: Instant)

  override val run: IO[Unit] =
    withRunningEmbeddedMongo("localhost", 27017) {
      MongoClient.fromConnectionString[IO]("mongodb://localhost:27017").use { client =>
        for {
          db   <- client.getDatabase("my-db")
          coll <- db.getCollectionWithCodec[Person]("people")
          person1 = Person("John", "Bloggs", Address("New-York", "USA"), Instant.now())
          person2 = Person("John", "Doe", Address("Los-Angeles", "USA"), Instant.now())
          person3 = Person("John", "Smith", Address("Chicago", "USA"), Instant.now())
          _                 <- coll.insertMany(List(person1, person2, person3))
          distinctAddresses <- coll.distinctWithCodec[Address]("address").all
          _                 <- IO.println(distinctAddresses)
        } yield ()
      }
    }
}
