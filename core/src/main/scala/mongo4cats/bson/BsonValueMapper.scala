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
  implicit val objectIdMapper: BsonValueMapper[ObjectId]         = value => BsonObjectId(value)
  implicit val intMapper: BsonValueMapper[Int]                   = value => BsonInt32(value)
  implicit val longMapper: BsonValueMapper[Long]                 = value => BsonInt64(value)
  implicit val stringMapper: BsonValueMapper[String]             = value => BsonString(value)
  implicit val dateTimeMapper: BsonValueMapper[Instant]          = value => BsonDateTime(value)
  implicit val arrayMapper: BsonValueMapper[Iterable[BsonValue]] = value => BsonArray(value)
  implicit val doubleMapper: BsonValueMapper[Double]             = value => BsonDouble(value)
  implicit val booleanMapper: BsonValueMapper[Boolean]           = value => BsonBoolean(value)
  implicit val documentMapper: BsonValueMapper[Document]         = value => BsonDocument(value)
}
