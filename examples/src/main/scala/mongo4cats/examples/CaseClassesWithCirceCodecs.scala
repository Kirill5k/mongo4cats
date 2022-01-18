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
import mongo4cats.client.MongoClient
import mongo4cats.circe.implicits._
import mongo4cats.circe.unsafe
import mongo4cats.embedded.EmbeddedMongo

import java.time.Instant

object CaseClassesWithCirceCodecs extends IOApp.Simple with EmbeddedMongo {

  final case class Address(city: String, country: String)
  final case class Person(
      firstName: String,
      lastName: String,
      address: Address,
      registrationDate: Instant
  )

  implicit val addressEnc = unsafe.circeDocumentEncoder[Address]
  implicit val personEnc = unsafe.circeDocumentEncoder[Person]

  override val run: IO[Unit] =
    withRunningEmbeddedMongo("localhost", 27017) {
      MongoClient.fromConnectionString[IO]("mongodb://localhost:27017").use { client =>
        for {
          db <- client.getDatabase[IO]("testdb")
          coll <- db.getCollection[IO]("people")
          person = Person("John", "Bloggs", Address("New-York", "USA"), Instant.now())
          _ <- coll.insertOne[IO, Person](person)
          docs <- coll.find.stream[IO, Person].compile.toList
          _ <- IO.println(docs)
        } yield ()
      }
    }
}
