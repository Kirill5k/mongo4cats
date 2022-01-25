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

trait BsonEncoder[A] extends Serializable { self =>
  def apply(a: A): BsonValue

  def contramap[B](f: B => A): BsonEncoder[B] = new BsonEncoder[B] {
    final def apply(a: B): BsonValue =
      self(f(a))
  }
}

trait BsonDocumentEncoder[A] extends Serializable { self =>
  def apply(a: A): BsonDocument

  def contramap[B](f: B => A): BsonDocumentEncoder[B] = new BsonDocumentEncoder[B] {
    final def apply(a: B): BsonDocument =
      self(f(a))
  }
}

object BsonDocumentEncoder {
  def apply[A](implicit ev: BsonDocumentEncoder[A]): BsonDocumentEncoder[A] = ev

  implicit val reify: BsonDocumentEncoder[BsonDocument] =
    new BsonDocumentEncoder[BsonDocument] {
      def apply(b: BsonDocument): BsonDocument = b
    }
}

object BsonEncoder {
  def apply[A](implicit ev: BsonEncoder[A]): BsonEncoder[A] = ev

  def instance[A](f: A => BsonValue) = new BsonEncoder[A] {
    def apply(a: A): BsonValue = f(a)
  }

  implicit val reify: BsonEncoder[BsonValue] =
    instance(x => x)
  implicit val int: BsonEncoder[Int] =
    instance(x => new BsonInt32(x))
  implicit val string: BsonEncoder[String] =
    instance(x => new BsonString(x))
  implicit val bsonArray: BsonEncoder[BsonArray] =
    instance(x => x: BsonValue)
  implicit val bsonBinary: BsonEncoder[BsonBinary] =
    instance(x => x: BsonValue)
  implicit val bsonBoolean: BsonEncoder[BsonBoolean] =
    instance(x => x: BsonValue)
  implicit val bsonDateTime: BsonEncoder[BsonDateTime] =
    instance(x => x: BsonDateTime)
  implicit val bsonDbPointer: BsonEncoder[BsonDbPointer] =
    instance(x => x: BsonValue)
  implicit val bsonDocument: BsonEncoder[BsonDocument] =
    instance(x => x: BsonValue)
  implicit val bsonJavaScript: BsonEncoder[BsonJavaScript] =
    instance(x => x: BsonValue)
  implicit val bsonJavaScriptWithScope: BsonEncoder[BsonJavaScriptWithScope] =
    instance(x => x: BsonValue)
  implicit val bsonMaxKey: BsonEncoder[BsonMaxKey] =
    instance(x => x: BsonValue)
  implicit val bsonMinKey: BsonEncoder[BsonMinKey] =
    instance(x => x: BsonValue)
  implicit val bsonNull: BsonEncoder[BsonNull] =
    instance(x => x: BsonValue)
  implicit val bsonNumber: BsonEncoder[BsonNumber] =
    instance(x => x: BsonValue)
  implicit val bsonObjectId: BsonEncoder[BsonObjectId] =
    instance(x => x: BsonValue)
  implicit val bsonRegex: BsonEncoder[BsonRegularExpression] =
    instance(x => x: BsonValue)
  implicit val bsonString: BsonEncoder[BsonString] =
    instance(x => x: BsonValue)
  implicit val bsonSymbol: BsonEncoder[BsonSymbol] =
    instance(x => x: BsonValue)
  implicit val bsonTimestamp: BsonEncoder[BsonTimestamp] =
    instance(x => x: BsonTimestamp)
  implicit val bsonUndefined: BsonEncoder[BsonUndefined] =
    instance(x => x: BsonValue)
  implicit val bsonDecimal128: BsonEncoder[BsonDecimal128] =
    instance(x => x: BsonValue)
  implicit val bsonDouble: BsonEncoder[BsonDouble] =
    instance(x => x: BsonValue)
  implicit val bsonInt: BsonEncoder[BsonInt32] =
    instance(x => x: BsonValue)
  implicit val bsonLong: BsonEncoder[BsonInt64] =
    instance(x => x: BsonValue)
}
