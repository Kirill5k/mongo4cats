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

import org.bson._

final case class DecodeError(msg: String) extends Throwable

trait Decoder[A] extends Serializable { self =>
  def apply(b: BsonValue): Either[DecodeError, A]

  def map[B](f: A => B): Decoder[B] = new Decoder[B] {
    def apply(b: BsonValue): Either[DecodeError, B] =
      self.apply(b).map(f)
  }
}

trait DocumentDecoder[A] extends Serializable { self =>
  def apply(b: BsonDocument): Either[DecodeError, A]

  def map[B](f: A => B): DocumentDecoder[B] = new DocumentDecoder[B] {
    def apply(b: BsonDocument): Either[DecodeError, B] =
      self.apply(b).map(f)
  }
}

object DocumentDecoder extends LowLevelDocumentDecoder {
  def apply[A](implicit ev: DocumentDecoder[A]): DocumentDecoder[A] = ev

  def instance[A](f: BsonDocument => Either[DecodeError, A]) = new DocumentDecoder[A] {
    def apply(b: BsonDocument) = f(b)
  }

  implicit val bsonDocumentDecoder: DocumentDecoder[BsonDocument] =
    DocumentDecoder.instance[BsonDocument](Right(_))
}

trait LowLevelDocumentDecoder {
  implicit def narrowDecoder[A: Decoder] = DocumentDecoder.instance[A] { (b: BsonDocument) =>
    Decoder[A].apply(b: BsonValue)
  }
}

object Decoder {
  def apply[A](implicit ev: Decoder[A]): Decoder[A] = ev

  def instance[A](f: BsonValue => Either[DecodeError, A]) = new Decoder[A] {
    def apply(b: BsonValue) = f(b)
  }

  implicit val bsonDecoder: Decoder[BsonValue] =
    Decoder.instance[BsonValue](Right(_))

  implicit val bsonDocumentDecoder: Decoder[BsonDocument] = Decoder.instance[BsonDocument] {
    case bd: BsonDocument => Right(bd)
    case _                => Left(DecodeError("Incorrect BsonDocument"))
  }

}
