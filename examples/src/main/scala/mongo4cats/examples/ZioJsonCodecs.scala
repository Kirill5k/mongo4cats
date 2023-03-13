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

import mongo4cats.zio.json._
import mongo4cats.zio.{ZMongoClient, ZMongoCollection, ZMongoDatabase}
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}
import zio._

import java.time.Instant

object ZioJsonCodecs extends ZIOAppDefault {

  final case class Address(
      city: String,
      country: String
  )

  object Address {
    implicit val dec: JsonDecoder[Address] = DeriveJsonDecoder.gen[Address]
    implicit val enc: JsonEncoder[Address] = DeriveJsonEncoder.gen[Address]
  }

  final case class Person(
      firstName: String,
      lastName: String,
      address: Address,
      registrationDate: Instant
  )

  object Person {
    implicit val dec: JsonDecoder[Person] = DeriveJsonDecoder.gen[Person]
    implicit val enc: JsonEncoder[Person] = DeriveJsonEncoder.gen[Person]
  }

  val client: TaskLayer[ZMongoClient] =
    ZLayer.scoped[Any](ZMongoClient.fromConnectionString("mongodb://localhost:27017"))

  val database: RLayer[ZMongoClient, ZMongoDatabase] =
    ZLayer.fromZIO(ZIO.serviceWithZIO[ZMongoClient](_.getDatabase("my-db")))

  val collection: RLayer[ZMongoDatabase, ZMongoCollection[Person]] =
    ZLayer.fromZIO(ZIO.serviceWithZIO[ZMongoDatabase](_.getCollectionWithCodec[Person]("people")))

  val program = for {
    coll <- ZIO.service[ZMongoCollection[Person]]
    people = List(
      Person("John", "Bloggs", Address("New-York", "USA"), Instant.now()),
      Person("John", "Doe", Address("Los-Angeles", "USA"), Instant.now()),
      Person("John", "Smith", Address("Chicago", "USA"), Instant.now())
    )
    _                 <- coll.insertMany(people)
    allPeople         <- coll.find.all
    _                 <- Console.printLine(allPeople)
    distinctAddresses <- coll.distinctWithCodec[Address]("address").all
    _                 <- Console.printLine(distinctAddresses)
  } yield ()

  override def run = program.provide(client, database, collection)
}
