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

/** The JSON integrations supply tree access without introducing a JSON dependency in the kernel. */
private[mongo4cats] trait JsonTree[J] {
  def preservesNegativeZero: Boolean = true
  def fields(value: J): Option[Vector[(String, J)]]
  def elements(value: J): Option[Vector[J]]
  def string(value: J): Option[String]
  def number(value: J): Option[String]
  def boolean(value: J): Option[Boolean]
  def isNull(value: J): Boolean

  def obj(fields: Vector[(String, J)]): J
  def arr(values: Vector[J]): J
  def str(value: String): J
  def num(value: String): J
  def bool(value: Boolean): J
  def nul: J
}
