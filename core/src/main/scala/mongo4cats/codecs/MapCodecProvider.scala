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
import org.bson.codecs.{
  BsonTypeClassMap,
  BsonTypeCodecMap,
  Codec,
  DecoderContext,
  EncoderContext,
  OverridableUuidRepresentationCodec
}
import org.bson.{BsonReader, BsonType, BsonWriter, Transformer, UuidRepresentation}

import scala.annotation.tailrec
import scala.reflect.ClassTag

final private class MapCodec(
    private val registry: CodecRegistry,
    private val valueTransformer: Transformer,
    private val bsonTypeClassMap: BsonTypeClassMap,
    private val uuidRepresentation: UuidRepresentation
) extends Codec[Map[String, Any]] with OverridableUuidRepresentationCodec[Map[String, Any]] {

  private val bsonTypeCodecMap: BsonTypeCodecMap = new BsonTypeCodecMap(bsonTypeClassMap, registry)

  override def withUuidRepresentation(newUuidRepresentation: UuidRepresentation): Codec[Map[String, Any]] =
    new MapCodec(registry, valueTransformer, bsonTypeClassMap, newUuidRepresentation)

  override def encode(writer: BsonWriter, map: Map[String, Any], encoderContext: EncoderContext): Unit = {
    writer.writeStartDocument()
    map.foreach { case (key, value) =>
      writer.writeName(key)
      ContainerValueReader.write(writer, encoderContext, Option(value), registry)
    }
    writer.writeEndDocument()
  }

  override def getEncoderClass: Class[Map[String, Any]] =
    implicitly[ClassTag[Map[String, Any]]].runtimeClass.asInstanceOf[Class[Map[String, Any]]]

  override def decode(reader: BsonReader, decoderContext: DecoderContext): Map[String, Any] = {
    @tailrec
    def go(result: Map[String, Any]): Map[String, Any] =
      if (reader.readBsonType() != BsonType.END_OF_DOCUMENT) {
        val key   = reader.readName()
        val value = ContainerValueReader.read(reader, decoderContext, bsonTypeCodecMap, uuidRepresentation, registry, valueTransformer)
        go(result + (key -> value))
      } else {
        result
      }
    reader.readStartDocument()
    val result = go(Map.empty)
    reader.readEndDocument()
    result
  }
}

object MapCodecProvider extends CodecProvider {

  override def get[T](clazz: Class[T], registry: CodecRegistry): Codec[T] =
    if (classOf[Map[String, Any]].isAssignableFrom(clazz))
      new MapCodec(
        registry,
        new DocumentToDBRefTransformer,
        new BsonTypeClassMap(),
        UuidRepresentation.UNSPECIFIED
      ).asInstanceOf[Codec[T]]
    else null
}
