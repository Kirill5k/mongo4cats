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

import com.mongodb.DocumentToDBRefTransformer
import mongo4cats.Clazz
import org.bson.codecs.configuration.CodecProvider
import org.bson.codecs.{BsonTypeClassMap, BsonTypeCodecMap, Codec, DecoderContext, EncoderContext, OverridableUuidRepresentationCodec}
import org.bson.{BsonReader, BsonType, BsonWriter, Transformer, UuidRepresentation}

final private class IterableCodec(
    private val registry: CodecRegistry,
    private val valueTransformer: Transformer,
    private val bsonTypeClassMap: BsonTypeClassMap,
    private val uuidRepresentation: UuidRepresentation
) extends Codec[Iterable[Any]] with OverridableUuidRepresentationCodec[Iterable[Any]] {

  private val bsonTypeCodecMap: BsonTypeCodecMap = new BsonTypeCodecMap(bsonTypeClassMap, registry)

  override def withUuidRepresentation(newUuidRepresentation: UuidRepresentation): Codec[Iterable[Any]] =
    new IterableCodec(registry, valueTransformer, bsonTypeClassMap, newUuidRepresentation)

  override def encode(writer: BsonWriter, iterable: Iterable[Any], encoderContext: EncoderContext): Unit = {
    writer.writeStartArray()
    for (value <- iterable)
      ContainerValueWriter.write(value, writer, encoderContext, registry)
    writer.writeEndArray()
  }

  override def getEncoderClass: Class[Iterable[Any]] = Clazz.tag[Iterable[Any]]

  override def decode(reader: BsonReader, decoderContext: DecoderContext): Iterable[Any] = {
    val result = scala.collection.mutable.ListBuffer.empty[Any]
    reader.readStartArray()
    while (reader.readBsonType() != BsonType.END_OF_DOCUMENT)
      result += ContainerValueReader.read(reader, decoderContext, bsonTypeCodecMap, uuidRepresentation, registry, valueTransformer)
    reader.readEndArray()
    result.toList
  }
}

object IterableCodecProvider extends CodecProvider {
  override def get[T](clazz: Class[T], registry: CodecRegistry): Codec[T] =
    if (classOf[Iterable[Any]].isAssignableFrom(clazz)) {
      new IterableCodec(
        registry,
        new DocumentToDBRefTransformer,
        new BsonTypeClassMap(),
        UuidRepresentation.STANDARD
      ).asInstanceOf[Codec[T]]
    } else null
}
