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
import scala.util.matching.Regex

sealed abstract class BsonValue {
  def isNull: Boolean
  def asInt: Option[Int]
  def asLong: Option[Long]
  def asDouble: Option[Double]
  def asBigDecimal: Option[BigDecimal]
  def asBoolean: Option[Boolean]
  def asString: Option[String]
  def asDocument: Option[Document]
  def asObjectId: Option[ObjectId]
  def asList: Option[List[BsonValue]]
  def asInstant: Option[Instant]
}

object BsonValue {
  case object BNull extends BsonValue {
    override def isNull: Boolean                  = true
    override def asInt: Option[Int]               = None
    override def asLong: Option[Long]             = None
    override def asDouble: Option[Double]         = None
    override def asBigDecimal: Option[BigDecimal] = None
    override def asBoolean: Option[Boolean]       = None
    override def asDocument: Option[Document]     = None
    override def asObjectId: Option[ObjectId]     = None
    override def asList: Option[List[BsonValue]]  = None
    override def asInstant: Option[Instant]       = None
    override def asString: Option[String]         = None
  }
  case object BUndefined extends BsonValue {
    override def isNull: Boolean                  = false
    override def asInt: Option[Int]               = None
    override def asLong: Option[Long]             = None
    override def asDouble: Option[Double]         = None
    override def asBigDecimal: Option[BigDecimal] = None
    override def asBoolean: Option[Boolean]       = None
    override def asDocument: Option[Document]     = None
    override def asObjectId: Option[ObjectId]     = None
    override def asList: Option[List[BsonValue]]  = None
    override def asInstant: Option[Instant]       = None
    override def asString: Option[String]         = None
  }
  case object BMaxKey extends BsonValue {
    override def isNull: Boolean                  = false
    override def asInt: Option[Int]               = None
    override def asLong: Option[Long]             = None
    override def asDouble: Option[Double]         = None
    override def asBigDecimal: Option[BigDecimal] = None
    override def asBoolean: Option[Boolean]       = None
    override def asDocument: Option[Document]     = None
    override def asObjectId: Option[ObjectId]     = None
    override def asList: Option[List[BsonValue]]  = None
    override def asInstant: Option[Instant]       = None
    override def asString: Option[String]         = None
  }
  case object BMinKey extends BsonValue {
    override def isNull: Boolean                  = false
    override def asInt: Option[Int]               = None
    override def asLong: Option[Long]             = None
    override def asDouble: Option[Double]         = None
    override def asBigDecimal: Option[BigDecimal] = None
    override def asBoolean: Option[Boolean]       = None
    override def asDocument: Option[Document]     = None
    override def asObjectId: Option[ObjectId]     = None
    override def asList: Option[List[BsonValue]]  = None
    override def asInstant: Option[Instant]       = None
    override def asString: Option[String]         = None
  }
  final case class BInt32(value: Int) extends BsonValue {
    override def isNull: Boolean                  = false
    override def asInt: Option[Int]               = Some(value)
    override def asLong: Option[Long]             = None
    override def asDouble: Option[Double]         = None
    override def asBigDecimal: Option[BigDecimal] = None
    override def asBoolean: Option[Boolean]       = None
    override def asDocument: Option[Document]     = None
    override def asObjectId: Option[ObjectId]     = None
    override def asList: Option[List[BsonValue]]  = None
    override def asInstant: Option[Instant]       = None
    override def asString: Option[String]         = None
  }
  final case class BInt64(value: Long) extends BsonValue {
    override def isNull: Boolean                  = false
    override def asInt: Option[Int]               = None
    override def asLong: Option[Long]             = Some(value)
    override def asDouble: Option[Double]         = None
    override def asBigDecimal: Option[BigDecimal] = None
    override def asBoolean: Option[Boolean]       = None
    override def asDocument: Option[Document]     = None
    override def asObjectId: Option[ObjectId]     = None
    override def asList: Option[List[BsonValue]]  = None
    override def asInstant: Option[Instant]       = None
    override def asString: Option[String]         = None
  }
  final case class BDouble(value: Double) extends BsonValue {
    override def isNull: Boolean                  = false
    override def asInt: Option[Int]               = None
    override def asLong: Option[Long]             = None
    override def asDouble: Option[Double]         = Some(value)
    override def asBigDecimal: Option[BigDecimal] = None
    override def asBoolean: Option[Boolean]       = None
    override def asDocument: Option[Document]     = None
    override def asObjectId: Option[ObjectId]     = None
    override def asList: Option[List[BsonValue]]  = None
    override def asInstant: Option[Instant]       = None
    override def asString: Option[String]         = None
  }
  final case class BDateTime(value: Instant) extends BsonValue {
    override def isNull: Boolean                  = false
    override def asInt: Option[Int]               = None
    override def asLong: Option[Long]             = None
    override def asDouble: Option[Double]         = None
    override def asBigDecimal: Option[BigDecimal] = None
    override def asBoolean: Option[Boolean]       = None
    override def asDocument: Option[Document]     = None
    override def asObjectId: Option[ObjectId]     = None
    override def asList: Option[List[BsonValue]]  = None
    override def asInstant: Option[Instant]       = Some(value)
    override def asString: Option[String]         = None
  }
  final case class BBinary(value: Array[Byte]) extends BsonValue {
    override def isNull: Boolean                  = false
    override def asInt: Option[Int]               = None
    override def asLong: Option[Long]             = None
    override def asDouble: Option[Double]         = None
    override def asBigDecimal: Option[BigDecimal] = None
    override def asBoolean: Option[Boolean]       = None
    override def asDocument: Option[Document]     = None
    override def asObjectId: Option[ObjectId]     = None
    override def asList: Option[List[BsonValue]]  = None
    override def asInstant: Option[Instant]       = None
    override def asString: Option[String]         = None
  }
  final case class BBoolean(value: Boolean) extends BsonValue {
    override def isNull: Boolean                  = false
    override def asInt: Option[Int]               = None
    override def asLong: Option[Long]             = None
    override def asDouble: Option[Double]         = None
    override def asBigDecimal: Option[BigDecimal] = None
    override def asBoolean: Option[Boolean]       = Some(value)
    override def asDocument: Option[Document]     = None
    override def asObjectId: Option[ObjectId]     = None
    override def asList: Option[List[BsonValue]]  = None
    override def asInstant: Option[Instant]       = None
    override def asString: Option[String]         = None
  }
  final case class BDecimal(value: BigDecimal) extends BsonValue {
    override def isNull: Boolean                  = false
    override def asInt: Option[Int]               = None
    override def asLong: Option[Long]             = None
    override def asDouble: Option[Double]         = None
    override def asBigDecimal: Option[BigDecimal] = Some(value)
    override def asBoolean: Option[Boolean]       = None
    override def asDocument: Option[Document]     = None
    override def asObjectId: Option[ObjectId]     = None
    override def asList: Option[List[BsonValue]]  = None
    override def asInstant: Option[Instant]       = None
    override def asString: Option[String]         = None
  }
  final case class BString(value: String) extends BsonValue {
    override def isNull: Boolean                  = false
    override def asInt: Option[Int]               = None
    override def asLong: Option[Long]             = None
    override def asDouble: Option[Double]         = None
    override def asBigDecimal: Option[BigDecimal] = None
    override def asBoolean: Option[Boolean]       = None
    override def asDocument: Option[Document]     = None
    override def asObjectId: Option[ObjectId]     = None
    override def asList: Option[List[BsonValue]]  = None
    override def asInstant: Option[Instant]       = None
    override def asString: Option[String]         = Some(value)
  }
  final case class BObjectId(value: ObjectId) extends BsonValue {
    override def isNull: Boolean                  = false
    override def asInt: Option[Int]               = None
    override def asLong: Option[Long]             = None
    override def asDouble: Option[Double]         = None
    override def asBigDecimal: Option[BigDecimal] = None
    override def asBoolean: Option[Boolean]       = None
    override def asDocument: Option[Document]     = None
    override def asObjectId: Option[ObjectId]     = Some(value)
    override def asList: Option[List[BsonValue]]  = None
    override def asInstant: Option[Instant]       = None
    override def asString: Option[String]         = None
  }
  final case class BDocument(value: Document) extends BsonValue {
    override def isNull: Boolean                  = false
    override def asInt: Option[Int]               = None
    override def asLong: Option[Long]             = None
    override def asDouble: Option[Double]         = None
    override def asBigDecimal: Option[BigDecimal] = None
    override def asBoolean: Option[Boolean]       = None
    override def asDocument: Option[Document]     = Some(value)
    override def asObjectId: Option[ObjectId]     = None
    override def asList: Option[List[BsonValue]]  = None
    override def asInstant: Option[Instant]       = None
    override def asString: Option[String]         = None
  }
  final case class BArray(value: Iterable[BsonValue]) extends BsonValue {
    override def isNull: Boolean                  = false
    override def asInt: Option[Int]               = None
    override def asLong: Option[Long]             = None
    override def asDouble: Option[Double]         = None
    override def asBigDecimal: Option[BigDecimal] = None
    override def asBoolean: Option[Boolean]       = None
    override def asDocument: Option[Document]     = None
    override def asObjectId: Option[ObjectId]     = None
    override def asList: Option[List[BsonValue]]  = Some(value.toList)
    override def asInstant: Option[Instant]       = None
    override def asString: Option[String]         = None
  }
  final case class BRegex(regex: Regex) extends BsonValue {
    override def isNull: Boolean                  = false
    override def asInt: Option[Int]               = None
    override def asLong: Option[Long]             = None
    override def asDouble: Option[Double]         = None
    override def asBigDecimal: Option[BigDecimal] = None
    override def asBoolean: Option[Boolean]       = None
    override def asDocument: Option[Document]     = None
    override def asObjectId: Option[ObjectId]     = None
    override def asList: Option[List[BsonValue]]  = None
    override def asInstant: Option[Instant]       = None
    override def asString: Option[String]         = None
  }

  val Null: BsonValue      = BNull
  val Undefined: BsonValue = BUndefined
  val MaxKey: BsonValue    = BMaxKey
  val MinKey: BsonValue    = BMinKey
  val True: BsonValue      = BBoolean(true)
  val False: BsonValue     = BBoolean(false)

  def array(values: BsonValue*): BsonValue                                              = BArray(values.toList)
  def array(value: Iterable[BsonValue]): BsonValue                                      = BArray(value)
  def array[A](value: Iterable[A])(implicit valueMapper: BsonValueMapper[A]): BsonValue = BArray(value.map(valueMapper.toBsonValue))

  def int(value: Int): BsonValue               = BInt32(value)
  def long(value: Long): BsonValue             = BInt64(value)
  def objectId(value: ObjectId): BsonValue     = BObjectId(value)
  def document(value: Document): BsonValue     = BDocument(value)
  def string(value: String): BsonValue         = BString(value)
  def bigDecimal(value: BigDecimal): BsonValue = BDecimal(value)
  def boolean(value: Boolean): BsonValue       = BBoolean(value)
  def double(value: Double): BsonValue         = BDouble(value)
  def binary(value: Array[Byte]): BsonValue    = BBinary(value)
  def instant(value: Instant): BsonValue       = BDateTime(value)
  def regex(value: Regex): BsonValue           = BRegex(value)
}
