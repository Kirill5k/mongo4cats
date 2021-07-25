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

package mongo4cats.database.operations

import com.mongodb.client.model.Filters
import org.bson.conversions.Bson

trait Filter {

  /** Creates a filter that performs a logical AND of the provided filter.
    *
    * <blockquote><pre> eq("x", 1).and(lt("y", 3)) </pre></blockquote>
    *
    * will generate a MongoDB query like: <blockquote><pre> { $and: [{x : 1}, {y : {$lt : 3}}]} </pre></blockquote>
    *
    * @param anotherFilter
    *   the filter to and together
    * @return
    *   the filter
    */
  def and(anotherFilter: Filter): Filter

  /** Creates a filter that preforms a logical OR of the provided filter.
    *
    * @param anotherFilter
    *   the filter to or together
    * @return
    *   the filter
    */
  def or(anotherFilter: Filter): Filter

  /** Creates a filter that matches all documents that do not match the passed in filter. Lifts the current filter to create a valid "$not"
    * query:
    *
    * <blockquote><pre> eq("x", 1).not </pre></blockquote>
    *
    * will generate a MongoDB query like: <blockquote><pre> {x : $not: {$eq : 1}} </pre></blockquote>
    *
    * @return
    *   the filter
    */
  def not: Filter

  /** Creates a filter that performs a logical NOR operation on the specified filters.
    *
    * @param anotherFilter
    *   the filter to or together
    * @return
    *   the filter
    */
  def nor(anotherFilter: Filter): Filter

  private[database] def toBson: Bson
  private[operations] def filter: Bson
}

object Filter {}

final private case class FilterBuilder(
    override val filter: Bson
) extends Filter {

  override def not: Filter =
    FilterBuilder(Filters.not(filter))

  override def and(anotherFilter: Filter): Filter =
    FilterBuilder(Filters.and(filter, anotherFilter.filter))

  override def or(anotherFilter: Filter): Filter =
    FilterBuilder(Filters.or(filter, anotherFilter.filter))

  override def nor(anotherFilter: Filter): Filter =
    FilterBuilder(Filters.nor(filter, anotherFilter.filter))

  override private[database] def toBson = filter
}
