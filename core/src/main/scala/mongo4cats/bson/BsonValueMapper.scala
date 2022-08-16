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

package mongo4cats.bson

import java.time.Instant

trait BsonValueMapper[A] {
  def toBsonValue(value: A): BsonValue
}

object BsonValueMapper {
  implicit val bsonValueMapper: BsonValueMapper[BsonValue] = identity(_)
  implicit val objectIdMapper: BsonValueMapper[ObjectId]   = value => BsonValue.objectId(value)
  implicit val intMapper: BsonValueMapper[Int]             = value => BsonValue.int(value)
  implicit val longMapper: BsonValueMapper[Long]           = value => BsonValue.long(value)
  implicit val stringMapper: BsonValueMapper[String]       = value => BsonValue.string(value)
  implicit val dateTimeMapper: BsonValueMapper[Instant]    = value => BsonValue.dateTime(value)
  implicit val doubleMapper: BsonValueMapper[Double]       = value => BsonValue.double(value)
  implicit val booleanMapper: BsonValueMapper[Boolean]     = value => BsonValue.boolean(value)
  implicit val documentMapper: BsonValueMapper[Document]   = value => BsonValue.document(value)

  implicit def arrayListMapper[A](implicit elMapper: BsonValueMapper[A]): BsonValueMapper[List[A]] =
    value => BsonValue.array(value.map(elMapper.toBsonValue))

  implicit def optionMapper[A](implicit elMapper: BsonValueMapper[A]): BsonValueMapper[Option[A]] = {
    case Some(value) => elMapper.toBsonValue(value)
    case None        => BsonValue.Null
  }
}
