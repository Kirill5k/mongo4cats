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
import org.bson.codecs.{DecoderContext, Encoder, EncoderContext}
import org.bson.types.Decimal128
import org.bson._

import java.time.Instant

private[codecs] object ContainerValueWriter {

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
      case BsonValue.BNull            => writer.writeNull()
      case BsonValue.BUndefined       => writer.writeUndefined()
      case BsonValue.BMaxKey          => writer.writeMaxKey()
      case BsonValue.BMinKey          => writer.writeMinKey()
      case BsonValue.BInt32(value)    => writer.writeInt32(value)
      case BsonValue.BInt64(value)    => writer.writeInt64(value)
      case BsonValue.BDouble(value)   => writer.writeDouble(value)
      case BsonValue.BDateTime(value) => writer.writeDateTime(value.toEpochMilli)
      case BsonValue.BBinary(value)   => writer.writeBinaryData(new BsonBinary(value))
      case BsonValue.BBoolean(value)  => writer.writeBoolean(value)
      case BsonValue.BDecimal(value)  => writer.writeDecimal128(new Decimal128(value.bigDecimal))
      case BsonValue.BString(value)   => writer.writeString(value)
      case BsonValue.BObjectId(value) => writer.writeObjectId(value)
      case BsonValue.BDocument(value) => writeBsonDocument(value, writer, None)
      case BsonValue.BArray(value)    => writeBsonArray(value, writer)
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

  def readBsonValue(
      reader: BsonReader,
      context: DecoderContext,
      registry: CodecRegistry,
      valueTransformer: Transformer
  ): Option[BsonValue] =
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
      case BsonType.DOCUMENT =>
        val document = valueTransformer.transform(registry.get(classOf[Document]).decode(reader, context))
        Some(BsonValue.document(document.asInstanceOf[Document]))
      case BsonType.ARRAY =>
        val iterable = valueTransformer.transform(registry.get(classOf[Iterable[BsonValue]]).decode(reader, context))
        Some(BsonValue.array(iterable.asInstanceOf[Iterable[BsonValue]]))
      case BsonType.DOUBLE     => Some(BsonValue.double(reader.readDouble()))
      case BsonType.STRING     => Some(BsonValue.string(reader.readString()))
      case BsonType.INT32      => Some(BsonValue.int(reader.readInt32()))
      case BsonType.INT64      => Some(BsonValue.long(reader.readInt64()))
      case BsonType.DECIMAL128 => Some(BsonValue.bigDecimal(reader.readDecimal128().bigDecimalValue()))
      case BsonType.BINARY     => Some(BsonValue.binary(reader.readBinaryData().getData))
      case BsonType.OBJECT_ID  => Some(BsonValue.objectId(reader.readObjectId()))
      case BsonType.BOOLEAN    => Some(BsonValue.boolean(reader.readBoolean()))
      case BsonType.DATE_TIME  => Some(BsonValue.dateTime(Instant.ofEpochMilli(reader.readDateTime())))
      case _                   => None

      /* REMAINING TYPES:
      case BsonType.JAVASCRIPT_WITH_SCOPE => ???
      case BsonType.TIMESTAMP             => ???
      case BsonType.REGULAR_EXPRESSION    => ???
      case BsonType.DB_POINTER            => ???
      case BsonType.JAVASCRIPT            => ???
      case BsonType.SYMBOL                => ???
      case BsonType.END_OF_DOCUMENT       => ???
       */
    }
}
