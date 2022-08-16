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
import mongo4cats.bson.BsonValue
import org.bson.codecs.{Codec, DecoderContext, EncoderContext, OverridableUuidRepresentationCodec}
import org.bson.codecs.configuration.CodecProvider
import org.bson.{BsonReader, BsonWriter, Transformer, UuidRepresentation}

import scala.reflect.ClassTag

final private class BsonValueCodec(
    private val registry: CodecRegistry,
    private val valueTransformer: Transformer
) extends Codec[BsonValue] {

  override def encode(writer: BsonWriter, bsonValue: BsonValue, encoderContext: EncoderContext): Unit =
    ContainerValueWriter.writeBsonValue(bsonValue, writer)

  override def getEncoderClass: Class[BsonValue] =
    implicitly[ClassTag[BsonValue]].runtimeClass.asInstanceOf[Class[BsonValue]]

  override def decode(reader: BsonReader, decoderContext: DecoderContext): BsonValue = {
    //TODO: unsafe
    ContainerValueReader.readBsonValue(reader).orNull
  }
}

object BsonValueCodecProvider extends CodecProvider {

  override def get[T](clazz: Class[T], registry: CodecRegistry): Codec[T] =
    Option
      .when(classOf[BsonValue].isAssignableFrom(clazz)) {
        new BsonValueCodec(
          registry,
          new DocumentToDBRefTransformer
        ).asInstanceOf[Codec[T]]
      }
      .orNull
}
