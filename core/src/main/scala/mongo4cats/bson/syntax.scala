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

package mongo4cats.bson

import org.bson.{BsonDocument, BsonValue}

trait CodecOps {
  implicit final class BEncoderOps[A](val value: A) {
    def asBson(implicit encoder: Encoder[A]): BsonValue = encoder(value)
  }

  implicit final class BDocEncoderOps[A](val value: A) {
    def asBsonDoc(implicit encoder: DocumentEncoder[A]): BsonDocument = encoder(value)
  }

  implicit class BDecoderOps(val value: BsonValue) {
    def as[A: Decoder]: Either[DecodeError, A] = Decoder[A].apply(value)
  }

  implicit class BDocDecoderOps(val value: BsonDocument) {
    def as[A: DocumentDecoder]: Either[DecodeError, A] = DocumentDecoder[A].apply(value)
  }
}

object syntax extends CodecOps
