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

package mongo4cats.operations

import com.mongodb.client.model.Sorts
import mongo4cats.AsJava
import org.bson.conversions.Bson

class Sort(
    private val sorts: List[Bson]
) extends AnyRef with Serializable with AsJava {

  def this() = this(Nil)

  private def withSorts(sort: Bson): Sort =
    new Sort(sort :: sorts)

  /** Create a sort specification for an ascending sort on the given field.
    *
    * @param fieldNames
    *   the field name
    * @return
    *   the sort specification [[https://docs.mongodb.com/manual/reference/method/cursor.sort/#std-label-sort-asc-desc]]
    */
  def asc(fieldNames: String*): Sort =
    withSorts(Sorts.ascending(fieldNames: _*))

  /** Create a sort specification for a descending sort on the given field.
    *
    * @param fieldNames
    *   the field name
    * @return
    *   the sort specification [[https://docs.mongodb.com/manual/reference/method/cursor.sort/#std-label-sort-asc-desc]]
    */
  def desc(fieldNames: String*): Sort =
    withSorts(Sorts.descending(fieldNames: _*))

  /** Create a sort specification for the text score meta projection on the given field.
    *
    * @param fieldName
    *   the field name
    * @return
    *   the sort specification [[https://docs.mongodb.com/manual/reference/method/cursor.sort/#std-label-sort-metadata]]
    */
  def metaTextScore(fieldName: String): Sort =
    withSorts(Sorts.metaTextScore(fieldName))

  /** Combine multiple sort specifications. If any field names are repeated, the last one takes precedence.
    *
    * @param anotherSort
    *   the sort specifications
    * @return
    *   the combined sort specification
    */
  def combinedWith(anotherSort: Sort): Sort =
    new Sort(anotherSort.sorts ::: sorts)

  private[mongo4cats] def toBson: Bson = Sorts.orderBy(asJava(sorts.reverse))

  override def toString: String = sorts.reverse.mkString("[", ",", "]")
  override def hashCode(): Int  = sorts.hashCode()
  override def equals(other: Any): Boolean =
    Option(other) match {
      case Some(sort: Sort) => sort.sorts == sorts
      case _                => false
    }
}

object Sort extends Sort {}
