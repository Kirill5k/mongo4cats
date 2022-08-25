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

import mongo4cats.bson.Document
import mongo4cats.bson.syntax._
import mongo4cats.operations.{Filter, Projection}
import mongo4cats.zio.{ZMongoClient, ZMongoCollection, ZMongoDatabase}
import zio._

object Zio extends ZIOAppDefault {

  val client     = ZLayer.scoped[Any](ZMongoClient.fromConnectionString("mongodb://localhost:27017"))
  val database   = ZLayer.fromZIO(ZIO.serviceWithZIO[ZMongoClient](_.getDatabase("my-db")))
  val collection = ZLayer.fromZIO(ZIO.serviceWithZIO[ZMongoDatabase](_.getCollection("docs")))

  val program = for {
    coll <- ZIO.service[ZMongoCollection[Document]]
    _    <- coll.insertMany((0 to 100).map(i => Document("name" := s"doc-$i", "index" := i)))
    docs <- coll.find
      .filter(Filter.gte("index", 10) && Filter.regex("name", "doc-[1-9]0"))
      .projection(Projection.excludeId)
      .sortByDesc("name")
      .limit(5)
      .all
    _ <- Console.printLine(docs.mkString("[\n", ",\n", "]"))
  } yield ()

  override def run = program.provide(client, database, collection)
}
