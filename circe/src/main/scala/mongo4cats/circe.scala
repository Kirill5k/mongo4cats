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
import io.circe.{parser, Decoder => JDecoder, Encoder => JEncoder, Json}
import io.circe.syntax._
import mongo4cats.bson.{DecodeError, Decoder, Document, DocumentEncoder, Encoder}
import org.bson._

object circe extends JsonCodecs {
  private val RootTag = "a"

  trait Instances extends JsonCodecs {
    implicit def circeEncoderToEncoder[A: JEncoder] = new Encoder[A] {
      def apply(a: A): BsonValue = {
        val json = a.asJson
        val wrapped = Json.obj(RootTag := json)
        val bson = BsonDocument.parse(wrapped.noSpaces)
        bson.get(RootTag)
      }
    }

    implicit def circeDecoderToDecoder[A: JDecoder] = new Decoder[A] {
      def apply(b: BsonValue) = {
        val doc = Document(RootTag -> b).toJson()
        val json = parser.parse(doc)
        val decoder = JDecoder.instance[A](_.get[A](RootTag))
        json.flatMap(decoder.decodeJson(_)).leftMap(x => DecodeError(x.toString))
      }
    }
  }

  object unsafe {
    def circeDocumentEncoder[A: JEncoder] = new DocumentEncoder[A] {
      def apply(a: A): BsonDocument =
        BsonDocument.parse(a.asJson.noSpaces)
    }
  }

  object implicits extends Instances
}
