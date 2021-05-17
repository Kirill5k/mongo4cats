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

package mongo4cats.database

import java.time.Instant
import org.bson.codecs.{Codec, DecoderContext, EncoderContext}
import org.bson.codecs.configuration.{CodecProvider, CodecRegistry}
import org.bson.{BsonReader, BsonWriter}

private[database] object codecs {

  private case object InstantCodec extends Codec[Instant] {
    def encode(writer: BsonWriter, value: Instant, encoderContext: EncoderContext): Unit =
      writer.writeDateTime(value.toEpochMilli);

    def decode(reader: BsonReader, decoderContext: DecoderContext): Instant =
      Instant.ofEpochMilli(reader.readDateTime())

    def getEncoderClass(): Class[Instant] =
      classOf[Instant]
  }

  case object CustomCodecProvider extends CodecProvider {
    private val ClassOfInstant = classOf[Instant]

    def get[T](clazz: Class[T], registry: CodecRegistry): Codec[T] =
      (clazz match {
        case ClassOfInstant => InstantCodec
        case _              => null
      }).asInstanceOf[Codec[T]]
  }
}
