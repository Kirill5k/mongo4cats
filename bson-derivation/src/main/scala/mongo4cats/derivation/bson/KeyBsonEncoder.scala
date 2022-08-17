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

import cats.Contravariant

import java.util.UUID

/** A type class that provides a conversion from a value of type `A` to a string.
  *
  * This type class will be used to create strings for BSON keys when encoding `Map[A, ?]` instances as BSON.
  *
  * Note that if more than one value maps to the same string, the resulting BSON object may have fewer fields than the original map.
  */
trait KeyBsonEncoder[A] extends Serializable { self =>

  /** Convert a key value to a string. */
  def apply(key: A): String

  /** Construct an instance for type `B` from an instance for type `A`. */
  final def contramap[B](f: B => A): KeyBsonEncoder[B] = new KeyBsonEncoder[B] {
    final def apply(key: B): String = self(f(key))
  }
}

object KeyBsonEncoder {
  @inline def apply[A](implicit A: KeyBsonEncoder[A]): KeyBsonEncoder[A] = A

  def instance[A](f: A => String): KeyBsonEncoder[A] = f(_)

  implicit val encodeKeyString: KeyBsonEncoder[String] = s => s
  implicit val encodeKeySymbol: KeyBsonEncoder[Symbol] = _.name
  implicit val encodeKeyUUID: KeyBsonEncoder[UUID]     = _.toString
  implicit val encodeKeyByte: KeyBsonEncoder[Byte]     = java.lang.Byte.toString(_)
  implicit val encodeKeyShort: KeyBsonEncoder[Short]   = java.lang.Short.toString(_)
  implicit val encodeKeyInt: KeyBsonEncoder[Int]       = java.lang.Integer.toString(_)
  implicit val encodeKeyLong: KeyBsonEncoder[Long]     = java.lang.Long.toString(_)

  implicit val keyEncoderContravariant: Contravariant[KeyBsonEncoder] =
    new Contravariant[KeyBsonEncoder] {
      final def contramap[A, B](e: KeyBsonEncoder[A])(f: B => A): KeyBsonEncoder[B] = e.contramap(f)
    }
}
