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

import org.bson.BsonValue

/** A type class that provides a way to produce a value of type `A` from a [[org.bson.BsonValue]] value. */
trait BsonDecoder[A] {

  /** Decode the given [[org.bson.BsonValue]]. */
  def apply(bson: BsonValue): BsonDecoder.Result[A]
}

object BsonDecoder {

  type Result[A] = Either[Throwable, A]

  def apply[A](implicit ev: BsonDecoder[A]): BsonDecoder[A] = ev

  def instance[A](f: BsonValue => Either[Throwable, A]): BsonDecoder[A] = f(_)
}
