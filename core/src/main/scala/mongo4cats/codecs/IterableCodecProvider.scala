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
import org.bson.codecs.configuration.CodecProvider
import org.bson.codecs.{BsonTypeClassMap, BsonTypeCodecMap, Codec, DecoderContext, EncoderContext, OverridableUuidRepresentationCodec}
import org.bson.{BsonReader, BsonType, BsonWriter, Transformer, UuidRepresentation}

import scala.annotation.tailrec
import scala.reflect.ClassTag

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
    iterable.foreach(value => ContainerValueWriter.write(value, writer, encoderContext, registry))
    writer.writeEndArray()
  }

  override def getEncoderClass: Class[Iterable[Any]] =
    implicitly[ClassTag[Iterable[Any]]].runtimeClass.asInstanceOf[Class[Iterable[Any]]]

  override def decode(reader: BsonReader, decoderContext: DecoderContext): Iterable[Any] = {
    @tailrec
    def go(result: List[Any]): List[Any] =
      if (reader.readBsonType() != BsonType.END_OF_DOCUMENT) {
        val value = ContainerValueReader.read(reader, decoderContext, bsonTypeCodecMap, uuidRepresentation, registry, valueTransformer)
        go(value :: result)
      } else {
        result.reverse
      }
    reader.readStartArray()
    val result = go(Nil)
    reader.readEndArray()
    result
  }
}

object IterableCodecProvider extends CodecProvider {
  override def get[T](clazz: Class[T], registry: CodecRegistry): Codec[T] =
    if (classOf[Iterable[Any]].isAssignableFrom(clazz)) {
      new IterableCodec(
        registry,
        new DocumentToDBRefTransformer,
        new BsonTypeClassMap(),
        UuidRepresentation.UNSPECIFIED
      ).asInstanceOf[Codec[T]]
    } else null
}
