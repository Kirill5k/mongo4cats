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

package mongo4cats.codecs

import mongo4cats.bson.{BsonValue, Document}
import org.bson.codecs.{BsonTypeCodecMap, DecoderContext}
import org.bson.internal.UuidHelper
import org.bson.{BsonReader, BsonType, Transformer, UuidRepresentation}

import java.time.Instant
import java.util.UUID

private[mongo4cats] object ContainerValueReader {

  def readBsonDocument(reader: BsonReader): Document = {
    val fields = scala.collection.mutable.Map.empty[String, BsonValue]
    reader.readStartDocument()
    while (reader.readBsonType() != BsonType.END_OF_DOCUMENT) {
      val key = reader.readName
      ContainerValueReader
        .readBsonValue(reader)
        .map(key -> _)
        .foreach(fields.addOne)
    }
    reader.readEndDocument()
    Document(fields)
  }

  private def readBsonArray(reader: BsonReader): Iterable[BsonValue] = {
    val result = scala.collection.mutable.ListBuffer.empty[BsonValue]
    reader.readStartArray()
    while (reader.readBsonType() != BsonType.END_OF_DOCUMENT) {
      ContainerValueReader
        .readBsonValue(reader)
        .foreach(result.addOne)
    }
    reader.readEndArray()
    result.toList
  }

  def readBsonValue(reader: BsonReader): Option[BsonValue] =
    reader.getCurrentBsonType match {
      case BsonType.MIN_KEY =>
        reader.readMinKey()
        Some(BsonValue.MinKey)
      case BsonType.MAX_KEY =>
        reader.readMinKey()
        Some(BsonValue.MaxKey)
      case BsonType.NULL =>
        reader.readNull()
        Some(BsonValue.Null)
      case BsonType.UNDEFINED =>
        reader.readUndefined()
        Some(BsonValue.Undefined)
      case BsonType.DOCUMENT   => Some(BsonValue.document(readBsonDocument(reader)))
      case BsonType.ARRAY      => Some(BsonValue.array(readBsonArray(reader)))
      case BsonType.DOUBLE     => Some(BsonValue.double(reader.readDouble()))
      case BsonType.STRING     => Some(BsonValue.string(reader.readString()))
      case BsonType.INT32      => Some(BsonValue.int(reader.readInt32()))
      case BsonType.INT64      => Some(BsonValue.long(reader.readInt64()))
      case BsonType.DECIMAL128 => Some(BsonValue.bigDecimal(reader.readDecimal128().bigDecimalValue()))
      case BsonType.BINARY if isUuid(reader, UuidRepresentation.STANDARD) =>
        val subtype = reader.peekBinarySubType()
        val binary  = reader.readBinaryData().getData
        Some(BsonValue.uuid(UuidHelper.decodeBinaryToUuid(binary, subtype, UuidRepresentation.STANDARD)))
      case BsonType.BINARY             => Some(BsonValue.binary(reader.readBinaryData().getData))
      case BsonType.OBJECT_ID          => Some(BsonValue.objectId(reader.readObjectId()))
      case BsonType.BOOLEAN            => Some(BsonValue.boolean(reader.readBoolean()))
      case BsonType.TIMESTAMP          => Some(BsonValue.timestamp(reader.readTimestamp().getTime.toLong))
      case BsonType.DATE_TIME          => Some(BsonValue.instant(Instant.ofEpochMilli(reader.readDateTime())))
      case BsonType.REGULAR_EXPRESSION => Some(BsonValue.regex(reader.readRegularExpression().asRegularExpression().getPattern.r))
      case _                           => None

      /* REMAINING TYPES:
      case BsonType.JAVASCRIPT_WITH_SCOPE => ??? // deprecated
      case BsonType.DB_POINTER            => ??? // deprecated
      case BsonType.SYMBOL                => ??? // deprecated
      case BsonType.JAVASCRIPT            => ???
      case BsonType.END_OF_DOCUMENT       => ???
       */
    }

  def read(
      reader: BsonReader,
      context: DecoderContext,
      bsonTypeCodecMap: BsonTypeCodecMap,
      uuidRepresentation: UuidRepresentation,
      registry: CodecRegistry,
      valueTransformer: Transformer
  ): AnyRef =
    reader.getCurrentBsonType match {
      case BsonType.ARRAY     => valueTransformer.transform(registry.get(classOf[Iterable[Any]]).decode(reader, context))
      case BsonType.DOCUMENT  => valueTransformer.transform(registry.get(classOf[Document]).decode(reader, context))
      case BsonType.DATE_TIME => valueTransformer.transform(registry.get(classOf[Instant]).decode(reader, context))
      case BsonType.BINARY if isUuid(reader, uuidRepresentation) =>
        valueTransformer.transform(registry.get(classOf[UUID]).decode(reader, context))
      case BsonType.NULL =>
        reader.readNull()
        null
      case BsonType.UNDEFINED =>
        reader.readUndefined()
        null
      case bsonType => valueTransformer.transform(bsonTypeCodecMap.get(bsonType).decode(reader, context))
    }

  private def isUuid(reader: BsonReader, uuidRepresentation: UuidRepresentation): Boolean =
    isLegacyUuid(reader, uuidRepresentation) || isStandardUuid(reader, uuidRepresentation)

  private def isLegacyUuid(reader: BsonReader, uuidRepresentation: UuidRepresentation): Boolean =
    reader.peekBinarySubType == 3 &&
      reader.peekBinarySize() == 16 &&
      (uuidRepresentation == UuidRepresentation.JAVA_LEGACY ||
        uuidRepresentation == UuidRepresentation.C_SHARP_LEGACY ||
        uuidRepresentation == UuidRepresentation.PYTHON_LEGACY)

  private def isStandardUuid(reader: BsonReader, uuidRepresentation: UuidRepresentation): Boolean =
    reader.peekBinarySubType == 4 &&
      reader.peekBinarySize() == 16 &&
      uuidRepresentation == UuidRepresentation.STANDARD
}
