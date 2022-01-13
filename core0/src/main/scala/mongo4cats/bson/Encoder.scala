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
}

object Encoder {
  def apply[A](implicit ev: Encoder[A]): Encoder[A] = ev
}
