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

package mongo4cats.derivation.bson

import cats.syntax.all._

/** A type class that provides a conversion from a string used as a BSON key to a value of type `A`.
  */
trait KeyBsonDecoder[A] extends Serializable { self =>

  /** Attempt to convert a String to a value.
    */
  def apply(key: String): Option[A]

  /** Construct an instance for type `B` from an instance for type `A`.
    */
  final def map[B](f: A => B): KeyBsonDecoder[B] = new KeyBsonDecoder[B] {
    final def apply(key: String): Option[B] = self(key).map(f)
  }

  /** Construct an instance for type `B` from an instance for type `A` given a monadic function.
    */
  final def flatMap[B](f: A => KeyBsonDecoder[B]): KeyBsonDecoder[B] = new KeyBsonDecoder[B] {
    final def apply(key: String): Option[B] = self(key).flatMap(a => f(a)(key))
  }
}

object KeyBsonDecoder {
  def apply[A](implicit A: KeyBsonDecoder[A]): KeyBsonDecoder[A] = A

  def instance[A](f: String => Option[A]): KeyBsonDecoder[A] = f(_)

  implicit val decodeKeyString: KeyBsonDecoder[String] =
    instance(_.some)

}
