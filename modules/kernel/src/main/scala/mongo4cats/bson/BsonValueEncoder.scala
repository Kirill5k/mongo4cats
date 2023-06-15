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
import java.util.UUID

trait BsonValueEncoder[A] {
  def encode(value: A): BsonValue
}

object BsonValueEncoder {
  implicit val bsonValueEncoder: BsonValueEncoder[BsonValue]   = identity(_)
  implicit val objectIdEncoder: BsonValueEncoder[ObjectId]     = value => BsonValue.objectId(value)
  implicit val intEncoder: BsonValueEncoder[Int]               = value => BsonValue.int(value)
  implicit val longEncoder: BsonValueEncoder[Long]             = value => BsonValue.long(value)
  implicit val stringEncoder: BsonValueEncoder[String]         = value => BsonValue.string(value)
  implicit val dateTimeEncoder: BsonValueEncoder[Instant]      = value => BsonValue.instant(value)
  implicit val doubleEncoder: BsonValueEncoder[Double]         = value => BsonValue.double(value)
  implicit val booleanEncoder: BsonValueEncoder[Boolean]       = value => BsonValue.boolean(value)
  implicit val documentEncoder: BsonValueEncoder[Document]     = value => BsonValue.document(value)
  implicit val bigDecimalEncoder: BsonValueEncoder[BigDecimal] = value => BsonValue.bigDecimal(value)
  implicit val uuidEncoder: BsonValueEncoder[UUID]             = value => BsonValue.uuid(value)

  implicit def arrayVectorEncoder[A](implicit e: BsonValueEncoder[A]): BsonValueEncoder[Vector[A]] =
    value => BsonValue.array(value.map(e.encode))

  implicit def arrayListEncoder[A](implicit e: BsonValueEncoder[A]): BsonValueEncoder[List[A]] =
    value => BsonValue.array(value.map(e.encode))

  implicit def optionEncoder[A](implicit e: BsonValueEncoder[A]): BsonValueEncoder[Option[A]] = {
    case Some(value) => e.encode(value)
    case None        => BsonValue.Null
  }
}
