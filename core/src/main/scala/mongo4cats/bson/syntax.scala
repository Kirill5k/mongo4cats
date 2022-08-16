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

object syntax {

  implicit final class KeySyntax(private val key: String) extends AnyVal {
    def :=[A](value: A)(implicit valueMapper: BsonValueMapper[A]): (String, BsonValue) =
      key -> valueMapper.toBsonValue(value)
  }

  implicit final class ValueSyntax[A](private val value: A) extends AnyVal {
    def toBson(implicit valueMapper: BsonValueMapper[A]): BsonValue = valueMapper.toBsonValue(value)
  }
}
