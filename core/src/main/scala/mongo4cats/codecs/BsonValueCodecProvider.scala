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

import mongo4cats.bson.BsonValue
import mongo4cats.helpers.clazz
import org.bson.codecs.configuration.CodecProvider
import org.bson.codecs.{Codec, DecoderContext, EncoderContext}
import org.bson.{BsonReader, BsonWriter}

private object BsonValueCodec extends Codec[BsonValue] {

  override def encode(writer: BsonWriter, bsonValue: BsonValue, encoderContext: EncoderContext): Unit =
    ContainerValueWriter.writeBsonValue(bsonValue, writer)

  override def getEncoderClass: Class[BsonValue] = clazz[BsonValue]

  override def decode(reader: BsonReader, decoderContext: DecoderContext): BsonValue =
    ContainerValueReader.readBsonValue(reader).orNull
}

object BsonValueCodecProvider extends CodecProvider {
  override def get[T](clazz: Class[T], registry: CodecRegistry): Codec[T] =
    if (classOf[BsonValue].isAssignableFrom(clazz)) BsonValueCodec.asInstanceOf[Codec[T]] else null
}
