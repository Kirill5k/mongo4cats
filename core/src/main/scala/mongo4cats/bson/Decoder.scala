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

  final def map[B](f: A => B) = new Decoder[B] {
    final def apply(b: BsonValue): Either[DecodeError, B] =
      self(b).map(f)
  }

  final def flatMap[B](f: A => Decoder[B]): Decoder[B] = new Decoder[B] {
    final def apply(b: BsonValue): Either[DecodeError, B] =
      self(b).flatMap(a => f(a)(b))
  }
}

trait DocumentDecoder[A] extends Serializable { self =>
  def apply(b: BsonDocument): Either[DecodeError, A]

  final def map[B](f: A => B) = new DocumentDecoder[B] {
    final def apply(b: BsonDocument): Either[DecodeError, B] =
      self(b).map(f)
  }

  final def flatMap[B](f: A => DocumentDecoder[B]): DocumentDecoder[B] =
    new DocumentDecoder[B] {
      final def apply(b: BsonDocument): Either[DecodeError, B] =
        self(b).flatMap(a => f(a)(b))
    }
}

object DocumentDecoder {
  def apply[A](implicit ev: DocumentDecoder[A]): DocumentDecoder[A] = ev

  implicit def narrowDecoder[A: Decoder]: DocumentDecoder[A] = new DocumentDecoder[A] {
    def apply(b: BsonDocument) = Decoder[A].apply(b: BsonValue)
  }

  implicit val bsonDocumentDecoder: DocumentDecoder[BsonDocument] =
    new DocumentDecoder[BsonDocument] {
      def apply(b: BsonDocument) = Right(b)
    }
}

object Decoder {
  def apply[A](implicit ev: Decoder[A]): Decoder[A] = ev

  implicit val bsonDecoder: Decoder[BsonValue] = new Decoder[BsonValue] {
    def apply(b: BsonValue) = Right(b)
  }
  implicit val document: Decoder[Document] = new Decoder[Document] {
    def apply(b: BsonValue) = b match {
      case b: BsonDocument => Right(Document.parse(b.toJson()))
      case _               => Left(DecodeError("Incorrect document"))

    }
  }
}
