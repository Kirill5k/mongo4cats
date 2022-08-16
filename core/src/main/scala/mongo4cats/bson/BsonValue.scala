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

sealed trait BsonValue

object BsonValue {
  case object BNull                                   extends BsonValue
  case object BUndefined                              extends BsonValue
  case object BMaxKey                                 extends BsonValue
  case object BMinKey                                 extends BsonValue
  final case class BInt32(value: Int)                 extends BsonValue
  final case class BInt64(value: Long)                extends BsonValue
  final case class BDouble(value: Double)             extends BsonValue
  final case class BDateTime(value: Instant)          extends BsonValue
  final case class BBinary(value: Array[Byte])        extends BsonValue
  final case class BBoolean(value: Boolean)           extends BsonValue
  final case class BDecimal(value: BigDecimal)        extends BsonValue
  final case class BString(value: String)             extends BsonValue
  final case class BObjectId(value: ObjectId)         extends BsonValue
  final case class BDocument(value: Document)         extends BsonValue
  final case class BArray(value: Iterable[BsonValue]) extends BsonValue

  val Null: BsonValue      = BNull
  val Undefined: BsonValue = BUndefined
  val MaxKey: BsonValue    = BMaxKey
  val MinKey: BsonValue    = BMinKey

  def array(value: Iterable[BsonValue]): BsonValue = BArray(value)
  def int(value: Int): BsonValue                   = BInt32(value)
  def long(value: Long): BsonValue                 = BInt64(value)
  def objectId(value: ObjectId): BsonValue         = BObjectId(value)
  def document(value: Document): BsonValue         = BDocument(value)
  def string(value: String): BsonValue             = BString(value)
  def bigDecimal(value: BigDecimal): BsonValue     = BDecimal(value)
  def boolean(value: Boolean): BsonValue           = BBoolean(value)
  def double(value: Double): BsonValue             = BDouble(value)
  def binary(value: Array[Byte]): BsonValue        = BBinary(value)
  def dateTime(value: Instant): BsonValue          = BDateTime(value)
}
