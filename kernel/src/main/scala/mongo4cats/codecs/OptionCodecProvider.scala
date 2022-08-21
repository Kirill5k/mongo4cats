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
import org.bson.codecs.{BsonTypeClassMap, BsonTypeCodecMap, Codec, DecoderContext, EncoderContext, OverridableUuidRepresentationCodec}
import org.bson.codecs.configuration.CodecProvider
import org.bson.{BsonReader, BsonWriter, Transformer, UuidRepresentation}

final private class OptionCodec(
    private val registry: CodecRegistry,
    private val valueTransformer: Transformer,
    private val bsonTypeClassMap: BsonTypeClassMap,
    private val uuidRepresentation: UuidRepresentation
) extends Codec[Option[Any]] with OverridableUuidRepresentationCodec[Option[Any]] {

  private val bsonTypeCodecMap: BsonTypeCodecMap = new BsonTypeCodecMap(bsonTypeClassMap, registry)

  override def withUuidRepresentation(newUuidRepresentation: UuidRepresentation): Codec[Option[Any]] =
    new OptionCodec(registry, valueTransformer, bsonTypeClassMap, newUuidRepresentation)

  override def encode(writer: BsonWriter, maybeValue: Option[Any], encoderContext: EncoderContext): Unit =
    maybeValue match {
      case Some(value) => ContainerValueWriter.write(value, writer, encoderContext, registry)
      case None        => writer.writeNull()
    }

  override def getEncoderClass: Class[Option[Any]] = Clazz.tag[Option[Any]]

  override def decode(reader: BsonReader, decoderContext: DecoderContext): Option[Any] =
    Option(ContainerValueReader.read(reader, decoderContext, bsonTypeCodecMap, uuidRepresentation, registry, valueTransformer))
}

object OptionCodecProvider extends CodecProvider {

  override def get[T](clazz: Class[T], registry: CodecRegistry): Codec[T] =
    if (classOf[Option[Any]].isAssignableFrom(clazz)) {
      new OptionCodec(
        registry,
        new DocumentToDBRefTransformer,
        new BsonTypeClassMap(),
        UuidRepresentation.UNSPECIFIED
      ).asInstanceOf[Codec[T]]
    } else null
}
