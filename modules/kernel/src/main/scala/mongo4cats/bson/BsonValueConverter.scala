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

import mongo4cats.AsScala
import mongo4cats.bson.BsonValue.{
  BArray,
  BBinary,
  BBoolean,
  BDateTime,
  BDecimal,
  BDocument,
  BDouble,
  BInt32,
  BInt64,
  BMaxKey,
  BMinKey,
  BNull,
  BObjectId,
  BRegex,
  BString,
  BTimestamp,
  BUndefined,
  BUuid
}
import org.bson.{BsonBinarySubType, BsonDocument => JBsonDocument, BsonType, BsonValue => JBsonValue, Document => JDocument}

import java.time.Instant
import java.util.UUID

object BsonValueConverter extends AsScala {
  def fromJava(jv: JBsonValue): BsonValue =
    jv.getBsonType match {
      case BsonType.NULL      => BNull
      case BsonType.UNDEFINED => BUndefined
      case BsonType.MAX_KEY   => BMaxKey
      case BsonType.MIN_KEY   => BMinKey
      case BsonType.INT32     => BInt32(jv.asInt32.getValue)
      case BsonType.INT64     => BInt64(jv.asInt64.getValue)
      case BsonType.DOUBLE    => BDouble(jv.asDouble.getValue)
      case BsonType.TIMESTAMP => BTimestamp(jv.asTimestamp.getTime.toLong, jv.asTimestamp.getInc)
      case BsonType.DATE_TIME => BDateTime(Instant.ofEpochMilli(jv.asDateTime.getValue))
      case BsonType.BINARY    =>
        val bin = jv.asBinary()
        if (bin.getType == BsonBinarySubType.UUID_STANDARD.getValue) BUuid(UUID.nameUUIDFromBytes(bin.getData)) else BBinary(bin.getData)
      case BsonType.BOOLEAN            => BBoolean(jv.asBoolean.getValue)
      case BsonType.DECIMAL128         => BDecimal(jv.asDecimal128.getValue.bigDecimalValue())
      case BsonType.STRING             => BString(jv.asString.getValue)
      case BsonType.OBJECT_ID          => BObjectId(jv.asObjectId.getValue)
      case BsonType.DOCUMENT           => BDocument(Document.fromJava(jv.asDocument))
      case BsonType.ARRAY              => BArray(asScala(jv.asArray.getValues).map(fromJava))
      case BsonType.REGULAR_EXPRESSION => BRegex(jv.asRegularExpression.getPattern.r)
      case bsonType                    => throw new IllegalArgumentException(s"unsupported bson type $bsonType")
    }

  def fromAny(value: Any): BsonValue = value match {
    case null                            => BsonValue.Null
    case v: JBsonDocument                => BsonValue.document(Document.fromJava(v))
    case v: JDocument                    => BsonValue.document(Document.fromJava(v))
    case v: ObjectId                     => BsonValue.objectId(v)
    case v: JBsonValue                   => fromJava(v)
    case v: BsonValue                    => v
    case v: org.bson.types.Binary        => BBinary(v.getData)
    case v: org.bson.types.BasicBSONList => BsonValue.array(asScala(v).map(fromAny))
    case v: org.bson.types.BSONTimestamp => BsonValue.timestamp(v.getTime.toLong, v.getInc)
    case v: org.bson.types.CodeWScope    => BsonValue.string(v.getCode)
    case v: org.bson.types.CodeWithScope => BsonValue.string(v.getCode)
    case v: org.bson.types.Code          => BsonValue.string(v.getCode)
    case v: org.bson.types.Decimal128    => BsonValue.bigDecimal(v.bigDecimalValue())
    case _: org.bson.types.MaxKey        => BMaxKey
    case _: org.bson.types.MinKey        => BMinKey
    case v: org.bson.types.Symbol        => BsonValue.string(v.getSymbol)
    case v: java.lang.String             => BsonValue.string(v)
    case v: java.lang.Integer            => BsonValue.int(v.intValue())
    case v: java.lang.Long               => BsonValue.long(v.longValue())
    case v: java.lang.Double             => BsonValue.double(v.doubleValue())
    case v: java.lang.Float              => BsonValue.double(v.doubleValue())
    case v: java.lang.Short              => BsonValue.int(v.intValue())
    case v: java.lang.Byte               => BsonValue.int(v.intValue())
    case v: java.lang.Boolean            => BsonValue.boolean(v.booleanValue())
    case v: java.lang.Character          => BsonValue.string(v.toString)
    case v: java.math.BigDecimal         => BsonValue.bigDecimal(BigDecimal(v))
    case v: java.math.BigInteger         => BsonValue.bigInt(BigInt(v))
    case v: java.util.Date               => BsonValue.instant(v.toInstant)
    case v: java.util.UUID               => BsonValue.uuid(v)
    case v: java.util.regex.Pattern      => BsonValue.regex(v.pattern().r)
    case v: Array[Byte]                  => BsonValue.binary(v)
    case v: java.util.List[_]            => BsonValue.array(asScala(v).map(fromAny))
    case v: Iterable[_]                  => BsonValue.array(v.map(fromAny))
    case v: java.util.Map[_, _]          =>
      BsonValue.document(Document(asScala(v).iterator.collect { case (k: String, vv) => k -> fromAny(vv) }.toList))
    case v: Product if v.productArity == 0 => BsonValue.string(v.toString)
    case other => throw new IllegalArgumentException(s"Unsupported value type in Java Document conversion: ${other.getClass.getName}")
  }
}
