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
