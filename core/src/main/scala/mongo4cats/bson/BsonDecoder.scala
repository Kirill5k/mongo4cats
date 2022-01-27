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

final case class BsonDecodeError(msg: String) extends Exception(msg)

trait BsonDecoder[A] extends Serializable { self =>
  def apply(b: BsonValue): Either[BsonDecodeError, A]

  def map[B](f: A => B): BsonDecoder[B] = new BsonDecoder[B] {
    def apply(b: BsonValue): Either[BsonDecodeError, B] =
      self.apply(b).map(f)
  }
}

trait BsonDocumentDecoder[A] extends Serializable { self =>
  def apply(b: BsonDocument): Either[BsonDecodeError, A]

  def map[B](f: A => B): BsonDocumentDecoder[B] = new BsonDocumentDecoder[B] {
    def apply(b: BsonDocument): Either[BsonDecodeError, B] =
      self.apply(b).map(f)
  }
}

object BsonDocumentDecoder extends LowLevelDocumentDecoder {
  def apply[A](implicit ev: BsonDocumentDecoder[A]): BsonDocumentDecoder[A] = ev

  def instance[A](f: BsonDocument => Either[BsonDecodeError, A]) = new BsonDocumentDecoder[A] {
    def apply(b: BsonDocument) = f(b)
  }

  implicit val bsonDocumentDecoder: BsonDocumentDecoder[BsonDocument] =
    instance[BsonDocument](Right(_))
}

trait LowLevelDocumentDecoder {
  implicit def narrowDecoder[A: BsonDecoder] = BsonDocumentDecoder.instance[A] {
    (b: BsonDocument) =>
      BsonDecoder[A].apply(b: BsonValue)
  }
}

object BsonDecoder {
  def apply[A](implicit ev: BsonDecoder[A]): BsonDecoder[A] = ev

  def instance[A](f: BsonValue => Either[BsonDecodeError, A]) = new BsonDecoder[A] {
    def apply(b: BsonValue) = f(b)
  }

  implicit val bsonDecoder: BsonDecoder[BsonValue] =
    instance[BsonValue](Right(_))

  implicit val bsonDocumentDecoder: BsonDecoder[BsonDocument] =
    BsonDecoder.instance[BsonDocument] {
      case bd: BsonDocument => Right(bd)
      case _                => Left(BsonDecodeError("Incorrect BsonDocument"))
    }

}
