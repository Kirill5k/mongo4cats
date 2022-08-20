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

import io.circe.generic.auto._
import mongo4cats.bson.{Document, ObjectId}
import mongo4cats.circe._
import mongo4cats.bson.syntax._

import java.time.Instant

object DocumentsWithCirce extends App {

  final case class MyClass(
      _id: ObjectId,
      dateField: Instant,
      stringField: String,
      intField: Int,
      longField: Long,
      arrayField: List[String],
      optionField: Option[String]
  )

  val myClass = MyClass(
    _id = ObjectId.gen,
    dateField = Instant.now(),
    stringField = "string",
    intField = 1,
    longField = 1660999000L,
    arrayField = List("item1", "item2"),
    optionField = None
  )

  val doc = Document("_id" := ObjectId.gen, "myClasses" := List(myClass))

  val retrievedMyClasses = doc.getAs[List[MyClass]]("myClasses")

  println(doc.toJson)
  println(retrievedMyClasses)
}
