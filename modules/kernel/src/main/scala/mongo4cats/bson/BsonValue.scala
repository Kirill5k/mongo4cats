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

import mongo4cats.{AsJava, AsScala, Uuid}
import org.bson.types.Decimal128
import org.bson.{
  BsonArray,
  BsonBinary,
  BsonBinarySubType,
  BsonBoolean,
  BsonDateTime,
  BsonDecimal128,
  BsonDouble,
  BsonInt32,
  BsonInt64,
  BsonMaxKey,
  BsonMinKey,
  BsonNull,
  BsonObjectId,
  BsonRegularExpression,
  BsonString,
  BsonTimestamp,
  BsonUndefined,
  BsonValue => JBsonValue
}

import java.time.Instant
import java.util.UUID
import scala.util.matching.Regex

sealed abstract class BsonValue {
  def isNull: Boolean
  def isUndefined: Boolean
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
  def asUuid: Option[UUID]

  def asJava: JBsonValue
}

object BsonValue extends AsScala {
  case object BNull extends BsonValue {
    override def isNull: Boolean                  = true
    override def isUndefined: Boolean             = false
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
    override def asUuid: Option[UUID]             = None

    override def asJava: JBsonValue = new BsonNull()
  }
  case object BUndefined extends BsonValue {
    override def isNull: Boolean                  = false
    override def isUndefined: Boolean             = true
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
    override def asUuid: Option[UUID]             = None

    override def asJava: JBsonValue = new BsonUndefined()
  }
  case object BMaxKey extends BsonValue {
    override def isNull: Boolean                  = false
    override def isUndefined: Boolean             = false
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
    override def asUuid: Option[UUID]             = None

    override def asJava: JBsonValue = new BsonMaxKey()
  }
  case object BMinKey extends BsonValue {
    override def isNull: Boolean                  = false
    override def isUndefined: Boolean             = false
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
    override def asUuid: Option[UUID]             = None

    override def asJava: JBsonValue = new BsonMinKey()
  }
  final case class BInt32(value: Int) extends BsonValue {
    override def isNull: Boolean                  = false
    override def isUndefined: Boolean             = false
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
    override def asUuid: Option[UUID]             = None

    override def asJava: JBsonValue = new BsonInt32(value)
  }
  final case class BInt64(value: Long) extends BsonValue {
    override def isNull: Boolean                  = false
    override def isUndefined: Boolean             = false
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
    override def asUuid: Option[UUID]             = None

    override def asJava: JBsonValue = new BsonInt64(value)
  }
  final case class BDouble(value: Double) extends BsonValue {
    override def isNull: Boolean                  = false
    override def isUndefined: Boolean             = false
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
    override def asUuid: Option[UUID]             = None

    override def asJava: JBsonValue = new BsonDouble(value)
  }
  final case class BTimestamp(seconds: Long, inc: Int) extends BsonValue {
    override def isNull: Boolean                  = false
    override def isUndefined: Boolean             = false
    override def asInt: Option[Int]               = None
    override def asLong: Option[Long]             = Some(seconds)
    override def asDouble: Option[Double]         = None
    override def asBigDecimal: Option[BigDecimal] = None
    override def asBoolean: Option[Boolean]       = None
    override def asDocument: Option[Document]     = None
    override def asObjectId: Option[ObjectId]     = None
    override def asList: Option[List[BsonValue]]  = None
    override def asInstant: Option[Instant]       = Some(Instant.ofEpochSecond(seconds))
    override def asString: Option[String]         = None
    override def asUuid: Option[UUID]             = None

    override def asJava: JBsonValue = new BsonTimestamp(seconds.toInt, inc)
  }
  final case class BDateTime(value: Instant) extends BsonValue {
    override def isNull: Boolean                  = false
    override def isUndefined: Boolean             = false
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
    override def asUuid: Option[UUID]             = None

    override def asJava: JBsonValue = new BsonDateTime(value.toEpochMilli)
  }
  final class BBinary private (val subtype: Byte, private val bytes: Array[Byte]) extends BsonValue with Product with Serializable {
    def this(value: Array[Byte], subtype: Byte = 0) = this(subtype, value.clone())

    // Keep the owned bytes private so values remain stable when used as hash keys.
    def value: Array[Byte] = bytes.clone()

    def copy(value: Array[Byte] = this.value, subtype: Byte = this.subtype): BBinary = new BBinary(value, subtype)

    override def isNull: Boolean                  = false
    override def isUndefined: Boolean             = false
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
    override def asUuid: Option[UUID]             = None

    override def asJava: JBsonValue = new BsonBinary(subtype, value)

    override def equals(other: Any): Boolean = other match {
      case that: BBinary => subtype == that.subtype && java.util.Arrays.equals(bytes, that.bytes)
      case _             => false
    }

    override def hashCode(): Int = 31 * java.util.Arrays.hashCode(bytes) + subtype.toInt

    override def canEqual(other: Any): Boolean = other.isInstanceOf[BBinary]
    override def productArity: Int             = 2
    override def productPrefix: String         = "BBinary"
    override def productElement(n: Int): Any   = n match {
      case 0 => value
      case 1 => subtype
      case _ => throw new IndexOutOfBoundsException(n.toString)
    }

    override def toString: String = scala.runtime.ScalaRunTime._toString(this)
  }
  object BBinary extends ((Array[Byte], Byte) => BBinary) {
    def apply(value: Array[Byte], subtype: Byte = 0): BBinary = new BBinary(value, subtype)

    def unapply(binary: BBinary): Some[(Array[Byte], Byte)] = Some((binary.value, binary.subtype))

    override def toString: String = "BBinary"
  }
  final case class BBoolean(value: Boolean) extends BsonValue {
    override def isNull: Boolean                  = false
    override def isUndefined: Boolean             = false
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
    override def asUuid: Option[UUID]             = None

    override def asJava: JBsonValue = new BsonBoolean(value)
  }
  final case class BDecimal(value: BigDecimal) extends BsonValue {
    override def isNull: Boolean                  = false
    override def isUndefined: Boolean             = false
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
    override def asUuid: Option[UUID]             = None

    override def asJava: JBsonValue = new BsonDecimal128(new Decimal128(value.bigDecimal))
  }
  final case class BString(value: String) extends BsonValue {
    override def isNull: Boolean                  = false
    override def isUndefined: Boolean             = false
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
    override def asUuid: Option[UUID]             = None

    override def asJava: JBsonValue = new BsonString(value)
  }
  final case class BObjectId(value: ObjectId) extends BsonValue {
    override def isNull: Boolean                  = false
    override def isUndefined: Boolean             = false
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
    override def asUuid: Option[UUID]             = None

    override def asJava: JBsonValue = new BsonObjectId(value)
  }
  final case class BDocument(value: Document) extends BsonValue {
    override def isNull: Boolean                  = false
    override def isUndefined: Boolean             = false
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
    override def asUuid: Option[UUID]             = None

    override def asJava: JBsonValue = value.toBsonDocument
  }
  final case class BArray(value: Iterable[BsonValue]) extends BsonValue with AsJava {
    override def isNull: Boolean                  = false
    override def isUndefined: Boolean             = false
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
    override def asUuid: Option[UUID]             = None

    override def asJava: JBsonValue = new BsonArray(asJava(value.map(_.asJava).toList))
  }
  final case class BRegex(value: Regex, options: String = "") extends BsonValue {
    override def isNull: Boolean                  = false
    override def isUndefined: Boolean             = false
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
    override def asUuid: Option[UUID]             = None

    override def asJava: JBsonValue = new BsonRegularExpression(value.pattern.pattern(), options)
  }

  final case class BUuid(value: UUID) extends BsonValue {
    override def isNull: Boolean                  = false
    override def isUndefined: Boolean             = false
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
    override def asUuid: Option[UUID]             = Some(value)

    override def asJava: JBsonValue = new BsonBinary(BsonBinarySubType.UUID_STANDARD, Uuid.toBinary(value))
  }

  val Null: BsonValue      = BNull
  val Undefined: BsonValue = BUndefined
  val MaxKey: BsonValue    = BMaxKey
  val MinKey: BsonValue    = BMinKey
  val True: BsonValue      = BBoolean(true)
  val False: BsonValue     = BBoolean(false)

  def array(values: BsonValue*): BsonValue                                     = BArray(values.toList)
  def array(value: Iterable[BsonValue]): BsonValue                             = BArray(value)
  def array[A](value: Iterable[A])(implicit e: BsonValueEncoder[A]): BsonValue = BArray(value.map(e.encode))

  def int(value: Int): BsonValue                           = BInt32(value)
  def long(value: Long): BsonValue                         = BInt64(value)
  def objectId(value: ObjectId): BsonValue                 = BObjectId(value)
  def document(value: Document): BsonValue                 = BDocument(value)
  def document(keyValues: (String, BsonValue)*): BsonValue = BDocument(Document(keyValues))
  def string(value: String): BsonValue                     = BString(value)
  def bigDecimal(value: BigDecimal): BsonValue             = BDecimal(value)
  def bigInt(value: BigInt): BsonValue                     = BDecimal(BigDecimal(value))
  def boolean(value: Boolean): BsonValue                   = BBoolean(value)
  def double(value: Double): BsonValue                     = BDouble(value)
  def binary(value: Array[Byte]): BsonValue                = BBinary(value)
  def binary(value: Array[Byte], subtype: Byte): BsonValue = BBinary(value, subtype)
  def instant(value: Instant): BsonValue                   = BDateTime(value)
  def regex(value: Regex): BsonValue                       = BRegex(value)
  def regex(value: Regex, options: String): BsonValue      = BRegex(value, options)
  def timestamp(seconds: Long, inc: Int = 1): BsonValue    = BTimestamp(seconds, inc)
  def uuid(value: UUID): BsonValue                         = BUuid(value)
}
