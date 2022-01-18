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

trait Encoder[A] extends Serializable { self =>
  def apply(a: A): BsonValue

  def contramap[B](f: B => A): Encoder[B] = new Encoder[B] {
    final def apply(a: B): BsonValue =
      self(f(a))
  }
}

trait DocumentEncoder[A] extends Serializable { self =>
  def apply(a: A): BsonDocument

  def contramap[B](f: B => A): DocumentEncoder[B] = new DocumentEncoder[B] {
    final def apply(a: B): BsonDocument =
      self(f(a))
  }
}

object DocumentEncoder {
  def apply[A](implicit ev: DocumentEncoder[A]): DocumentEncoder[A] = ev

  implicit val reify: DocumentEncoder[BsonDocument] = new DocumentEncoder[BsonDocument] {
    def apply(b: BsonDocument): BsonDocument = b
  }

  implicit val document: DocumentEncoder[Document] = new DocumentEncoder[Document] {
    def apply(b: Document): BsonDocument = b.toBsonDocument
  }
}

object Encoder {
  def apply[A](implicit ev: Encoder[A]): Encoder[A] = ev

  def instance[A](f: A => BsonValue) = new Encoder[A] {
    def apply(a: A): BsonValue = f(a)
  }

  implicit val reify: Encoder[BsonValue] =
    Encoder.instance(x => x)
  implicit val int: Encoder[Int] =
    Encoder.instance(x => new BsonInt32(x))
  implicit val string: Encoder[String] =
    Encoder.instance(x => new BsonString(x))
  implicit val bsonArray: Encoder[BsonArray] =
    Encoder.instance(x => x: BsonValue)
  implicit val bsonBinary: Encoder[BsonBinary] =
    Encoder.instance(x => x: BsonValue)
  implicit val bsonBoolean: Encoder[BsonBoolean] =
    Encoder.instance(x => x: BsonValue)
  implicit val bsonDateTime: Encoder[BsonDateTime] =
    Encoder.instance(x => x: BsonDateTime)
  implicit val bsonDbPointer: Encoder[BsonDbPointer] =
    Encoder.instance(x => x: BsonValue)
  implicit val bsonDocument: Encoder[BsonDocument] =
    Encoder.instance(x => x: BsonValue)
  implicit val bsonJavaScript: Encoder[BsonJavaScript] =
    Encoder.instance(x => x: BsonValue)
  implicit val bsonJavaScriptWithScope: Encoder[BsonJavaScriptWithScope] =
    Encoder.instance(x => x: BsonValue)
  implicit val bsonMaxKey: Encoder[BsonMaxKey] =
    Encoder.instance(x => x: BsonValue)
  implicit val bsonMinKey: Encoder[BsonMinKey] =
    Encoder.instance(x => x: BsonValue)
  implicit val bsonNull: Encoder[BsonNull] =
    Encoder.instance(x => x: BsonValue)
  implicit val bsonNumber: Encoder[BsonNumber] =
    Encoder.instance(x => x: BsonValue)
  implicit val bsonObjectId: Encoder[BsonObjectId] =
    Encoder.instance(x => x: BsonValue)
  implicit val bsonRegex: Encoder[BsonRegularExpression] =
    Encoder.instance(x => x: BsonValue)
  implicit val bsonString: Encoder[BsonString] =
    Encoder.instance(x => x: BsonValue)
  implicit val bsonSymbol: Encoder[BsonSymbol] =
    Encoder.instance(x => x: BsonValue)
  implicit val bsonTimestamp: Encoder[BsonTimestamp] =
    Encoder.instance(x => x: BsonTimestamp)
  implicit val bsonUndefined: Encoder[BsonUndefined] =
    Encoder.instance(x => x: BsonValue)
  implicit val bsonDecimal128: Encoder[BsonDecimal128] =
    Encoder.instance(x => x: BsonValue)
  implicit val bsonDouble: Encoder[BsonDouble] =
    Encoder.instance(x => x: BsonValue)
  implicit val bsonInt: Encoder[BsonInt32] =
    Encoder.instance(x => x: BsonValue)
  implicit val bsonLong: Encoder[BsonInt64] =
    Encoder.instance(x => x: BsonValue)
}
