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

trait Sort {

  /** Create a sort specification for an ascending sort on the given field.
    *
    * @param fieldNames
    *   the field name
    * @return
    *   the sort specification [[https://docs.mongodb.com/manual/reference/method/cursor.sort/#std-label-sort-asc-desc]]
    */
  def asc(fieldNames: String*): Sort

  /** Create a sort specification for a descending sort on the given field.
    *
    * @param fieldNames
    *   the field name
    * @return
    *   the sort specification [[https://docs.mongodb.com/manual/reference/method/cursor.sort/#std-label-sort-asc-desc]]
    */
  def desc(fieldNames: String*): Sort

  /** Create a sort specification for the text score meta projection on the given field.
    *
    * @param fieldName
    *   the field name
    * @return
    *   the sort specification [[https://docs.mongodb.com/manual/reference/method/cursor.sort/#std-label-sort-metadata]]
    */
  def metaTextScore(fieldName: String): Sort

  /** Combine multiple sort specifications. If any field names are repeated, the last one takes precedence.
    *
    * @param anotherSort
    *   the sort specifications
    * @return
    *   the combined sort specification
    */
  def combinedWith(anotherSort: Sort): Sort

  private[mongo4cats] def toBson: Bson
  private[mongo4cats] def sorts: List[Bson]
}

object Sort {
  private val empty: Sort = SortBuilder(Nil)

  def asc(fieldNames: String*): Sort         = empty.asc(fieldNames: _*)
  def desc(fieldNames: String*): Sort        = empty.desc(fieldNames: _*)
  def metaTextScore(fieldName: String): Sort = empty.metaTextScore(fieldName)
}

final private case class SortBuilder(
    override val sorts: List[Bson]
) extends Sort with AsJava {

  override def asc(fieldNames: String*): Sort         = SortBuilder(Sorts.ascending(fieldNames: _*) :: sorts)
  override def desc(fieldNames: String*): Sort        = SortBuilder(Sorts.descending(fieldNames: _*) :: sorts)
  override def metaTextScore(fieldName: String): Sort = SortBuilder(Sorts.metaTextScore(fieldName) :: sorts)

  override def combinedWith(anotherSort: Sort): Sort =
    SortBuilder(anotherSort.sorts ::: sorts)

  override private[mongo4cats] def toBson: Bson = Sorts.orderBy(asJava(sorts.reverse))
}
