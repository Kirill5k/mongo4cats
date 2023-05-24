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

trait BsonValueDecoder[A] {
  def decode(bsonValue: BsonValue): Option[A]
}

object BsonValueDecoder {
  implicit val objectIdDecoder: BsonValueDecoder[ObjectId]     = _.asObjectId
  implicit val intDecoder: BsonValueDecoder[Int]               = _.asInt
  implicit val longDecoder: BsonValueDecoder[Long]             = _.asLong
  implicit val stringDecoder: BsonValueDecoder[String]         = _.asString
  implicit val dateTimeDecoder: BsonValueDecoder[Instant]      = _.asInstant
  implicit val doubleDecoder: BsonValueDecoder[Double]         = _.asDouble
  implicit val booleanDecoder: BsonValueDecoder[Boolean]       = _.asBoolean
  implicit val documentDecoder: BsonValueDecoder[Document]     = _.asDocument
  implicit val bigDecimalDecoder: BsonValueDecoder[BigDecimal] = _.asBigDecimal

  implicit def arrayListDecoder[A](implicit d: BsonValueDecoder[A]): BsonValueDecoder[List[A]] =
    _.asList
      .flatMap { bsonValueList =>
        bsonValueList.foldLeft(Option(List.empty[A])) { (result, bv) =>
          result match {
            case Some(res) =>
              d.decode(bv) match {
                case Some(value) => Some(value :: res)
                case None        => None
              }
            case None => None
          }
        }
      }
      .map(_.reverse)
}
