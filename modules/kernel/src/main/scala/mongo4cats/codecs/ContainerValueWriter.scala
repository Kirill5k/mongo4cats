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
import org.bson._
import org.bson.codecs.{Encoder, EncoderContext}
import org.bson.internal.UuidHelper
import org.bson.types.Decimal128

private[mongo4cats] object ContainerValueWriter {

  def writeBsonDocument(
      value: Document,
      writer: BsonWriter,
      idFieldName: Option[String]
  ): Unit = {
    writer.writeStartDocument()

    idFieldName.flatMap(idName => value.getObjectId(idName).map(idName -> _)).foreach { case (idName, idValue) =>
      writer.writeName(idName)
      writer.writeObjectId(idValue)
    }

    value.toMap
      .filterNot { case (key, _) => idFieldName.contains(key) }
      .foreach { case (key, value) =>
        writer.writeName(key)
        writeBsonValue(value, writer)
      }

    writer.writeEndDocument()
  }

  def writeBsonArray(
      value: Iterable[BsonValue],
      writer: BsonWriter
  ): Unit = {
    writer.writeStartArray()
    value.foreach(value => writeBsonValue(value, writer))
    writer.writeEndArray()
  }

  def writeBsonValue(
      bsonValue: BsonValue,
      writer: BsonWriter
  ): Unit =
    bsonValue match {
      case BsonValue.BNull             => writer.writeNull()
      case BsonValue.BUndefined        => writer.writeUndefined()
      case BsonValue.BMaxKey           => writer.writeMaxKey()
      case BsonValue.BMinKey           => writer.writeMinKey()
      case BsonValue.BInt32(value)     => writer.writeInt32(value)
      case BsonValue.BInt64(value)     => writer.writeInt64(value)
      case BsonValue.BDouble(value)    => writer.writeDouble(value)
      case BsonValue.BTimestamp(value) => writer.writeTimestamp(new BsonTimestamp(value.toInt, 1))
      case BsonValue.BDateTime(value)  => writer.writeDateTime(value.toEpochMilli)
      case BsonValue.BUuid(value) =>
        writer.writeBinaryData(new BsonBinary(UuidHelper.encodeUuidToBinary(value, UuidRepresentation.STANDARD)))
      case BsonValue.BBinary(value)   => writer.writeBinaryData(new BsonBinary(value))
      case BsonValue.BBoolean(value)  => writer.writeBoolean(value)
      case BsonValue.BDecimal(value)  => writer.writeDecimal128(new Decimal128(value.bigDecimal))
      case BsonValue.BString(value)   => writer.writeString(value)
      case BsonValue.BObjectId(value) => writer.writeObjectId(value)
      case BsonValue.BDocument(value) => writeBsonDocument(value, writer, None)
      case BsonValue.BArray(value)    => writeBsonArray(value, writer)
      case BsonValue.BRegex(value)    => writer.writeRegularExpression(new BsonRegularExpression(value.pattern.pattern()))
    }

  def write(
      value: Any,
      writer: BsonWriter,
      context: EncoderContext,
      registry: CodecRegistry
  ): Unit =
    value match {
      case v: BsonValue => writeBsonValue(v, writer)
      case v            => context.encodeWithChildContext(registry.get(v.getClass).asInstanceOf[Encoder[Any]], writer, v)
    }
}
