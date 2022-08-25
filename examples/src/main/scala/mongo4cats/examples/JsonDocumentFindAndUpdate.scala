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
import mongo4cats.client.MongoClient
import mongo4cats.operations.{Filter, Update}
import mongo4cats.bson.Document

object JsonDocumentFindAndUpdate extends IOApp.Simple {

  val json =
    """{
      |"firstName": "John",
      |"lastName": "Bloggs",
      |"dob": "1970-01-01"
      |}""".stripMargin

  val filterQuery = Filter.eq("lastName", "Bloggs") || Filter.eq("firstName", "John")

  val updateQuery = Update
    .set("dob", "2020-01-01")
    .rename("firstName", "name")
    .currentTimestamp("updatedAt")
    .unset("lastName")

  val run: IO[Unit] =
    MongoClient.fromConnectionString[IO]("mongodb://localhost:27017").use { client =>
      for {
        db      <- client.getDatabase("testdb")
        coll    <- db.getCollection("jsoncoll")
        _       <- coll.insertOne(Document.parse(json))
        old     <- coll.findOneAndUpdate(filterQuery, updateQuery)
        updated <- coll.find.first
        _       <- IO.println(s"old: ${old.get.toJson}\nupdated: ${updated.get.toJson}")
      } yield ()
    }
}
