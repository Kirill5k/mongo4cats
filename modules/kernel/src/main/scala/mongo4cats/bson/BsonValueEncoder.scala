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
  implicit val objectIdEncoder: BsonValueEncoder[ObjectId]     = BsonValue.objectId(_)
  implicit val intEncoder: BsonValueEncoder[Int]               = BsonValue.int(_)
  implicit val longEncoder: BsonValueEncoder[Long]             = BsonValue.long(_)
  implicit val stringEncoder: BsonValueEncoder[String]         = BsonValue.string(_)
  implicit val dateTimeEncoder: BsonValueEncoder[Instant]      = BsonValue.instant(_)
  implicit val doubleEncoder: BsonValueEncoder[Double]         = BsonValue.double(_)
  implicit val booleanEncoder: BsonValueEncoder[Boolean]       = BsonValue.boolean(_)
  implicit val documentEncoder: BsonValueEncoder[Document]     = BsonValue.document(_)
  implicit val bigDecimalEncoder: BsonValueEncoder[BigDecimal] = BsonValue.bigDecimal(_)
  implicit val uuidEncoder: BsonValueEncoder[UUID]             = BsonValue.uuid(_)
  implicit val binaryEncoder: BsonValueEncoder[Array[Byte]]    = BsonValue.binary(_)

  implicit def arrayVectorEncoder[A](implicit e: BsonValueEncoder[A]): BsonValueEncoder[Vector[A]] =
    value => BsonValue.array(value.map(e.encode))

  implicit def arrayListEncoder[A](implicit e: BsonValueEncoder[A]): BsonValueEncoder[List[A]] =
    value => BsonValue.array(value.map(e.encode))

  implicit def optionEncoder[A](implicit e: BsonValueEncoder[A]): BsonValueEncoder[Option[A]] = {
    case Some(value) => e.encode(value)
    case None        => BsonValue.Null
  }
}
