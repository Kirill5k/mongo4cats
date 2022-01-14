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

package mongo4cats

import cats.implicits._
import io.circe.{parser, Decoder => JDecoder, Encoder => JEncoder}
import io.circe.syntax._
import org.bson.codecs.{BsonValueCodec, DecoderContext, EncoderContext}
import mongo4cats.bson.{DecodeError, Decoder, Encoder}
import org.bson._
import org.bson.json.{JsonReader, JsonWriter}
import java.io.{StringReader, StringWriter}

object circe extends JsonCodecs {

  implicit def circeEncoderToEncoder[A: JEncoder] = new Encoder[A] {
    def apply(a: A): BsonValue = {
      val json = a.asJson.noSpaces
      val stringReader = new StringReader(json)
      val reader = new JsonReader(stringReader)
      val codec = new BsonValueCodec()
      codec.decode(reader, DecoderContext.builder.build)
    }
  }

  implicit def circeDecoderToDecoder[A: JDecoder] = new Decoder[A] {
    def apply(b: BsonValue) = {
      val stringWriter = new StringWriter()
      val writer = new JsonWriter(stringWriter)
      val codec = new BsonValueCodec()
      codec.encode(writer, b, EncoderContext.builder.build)
      parser.parse(writer.toString).flatMap(_.as[A]).leftMap(x => DecodeError(x.toString))
    }
  }
}
