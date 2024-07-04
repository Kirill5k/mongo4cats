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

import com.mongodb.client.model.Projections
import mongo4cats.bson.{BsonValue, Document}
import mongo4cats.bson.syntax._
import org.bson.conversions.Bson

trait Projection {

  /** Creates a projection of a field whose value is computed from the given expression. Projection with an expression is only supported
    * using the \$project aggregation pipeline stage.
    *
    * @param fieldName
    *   the field name
    * @param expression
    *   the expression
    * @return
    *   the projection
    */
  def computed[T](fieldName: String, expression: T): Projection

  /** Creates a projection that includes all of the given fields.
    *
    * @param fieldName
    *   the field name
    * @return
    *   the projection
    */
  def include(fieldName: String): Projection

  /** Creates a projection that includes all of the given fields.
    *
    * @param fieldNames
    *   the field names
    * @return
    *   the projection
    */
  def include(fieldNames: Seq[String]): Projection

  /** Creates a projection that excludes all of the given fields.
    *
    * @param fieldName
    *   the field name
    * @return
    *   the projection
    */
  def exclude(fieldName: String): Projection

  /** Creates a projection that excludes all of the given fields.
    *
    * @param fieldNames
    *   the field names
    * @return
    *   the projection
    */
  def exclude(fieldNames: Seq[String]): Projection

  /** Creates a projection that excludes the _id field. This suppresses the automatic inclusion of _id that is the default, even when other
    * fields are explicitly included.
    *
    * @return
    *   the projection
    */
  def excludeId: Projection

  /** Creates a projection that includes for the given field only the first element of an array that matches the query filter. This is
    * referred to as the positional \$ operator.
    *
    * @param fieldName
    *   the field name whose value is the array
    * @return
    *   the projection
    */
  def elemMatch(fieldName: String): Projection

  /** Creates a projection that includes for the given field only the first element of the array value of that field that matches the given
    * query filter.
    *
    * @param fieldName
    *   the field name
    * @param filter
    *   the filter to apply
    * @return
    *   the projection
    */
  def elemMatch(fieldName: String, filter: Filter): Projection

  /** Creates a \$meta projection to the given field name for the given meta field name.
    *
    * @param fieldName
    *   the field name
    * @param metaFieldName
    *   the meta field name
    * @return
    *   the projection
    * @since 4.1
    */
  def meta(fieldName: String, metaFieldName: String): Projection

  /** Creates a projection to the given field name of the textScore, for use with text queries.
    *
    * @param fieldName
    *   the field name
    * @return
    *   the projection
    */
  def metaTextScore(fieldName: String): Projection

  /** Creates a projection to the given field name of the searchScore, for use with Aggregate.search(SearchOperator,SearchOptions) /
    * Aggregate.search(SearchCollector,SearchOptions). Calling this method is equivalent to calling meta(String,String) with "searchScore"
    * as the second argument.
    *
    * @param fieldName
    *   the field name
    * @return
    *   the projection
    * @since 4.7
    */
  def metaSearchScore(fieldName: String): Projection

  /** Creates a projection to the given field name of the searchHighlights, for use with Aggregate.search(SearchOperator,SearchOptions) /
    * Aggregates.search(SearchCollector,SearchOptions). Calling this method is equivalent to calling meta String,String) with
    * "searchHighlights" as the second argument.
    *
    * @param fieldName
    *   the field name
    * @return
    *   the projection
    * @since 4.7
    */
  def metaSearchHighlights(fieldName: String): Projection

  /** Creates a projection of a field whose value is equal to the \$\$SEARCH_META variable, for use with
    * Aggregate.search(SearchOperator,SearchOptions) / Aggregate.search(SearchCollector,SearchOptions). Calling this method is equivalent to
    * calling computed(String,Object) with "\$\$SEARCH_META" as the second argument.
    *
    * @param fieldName
    *   the field name
    * @return
    *   the projection
    * @since 4.7
    */
  def computedSearchMeta(fieldName: String): Projection

  /** Creates a projection to the given field name of a slice of the array value of that field.
    *
    * @param fieldName
    *   the field name
    * @param limit
    *   the number of elements to project.
    * @return
    *   the projection
    */
  def slice(fieldName: String, limit: Int): Projection

  /** Creates a projection to the given field name of a slice of the array value of that field.
    *
    * @param fieldName
    *   the field name
    * @param skip
    *   the number of elements to skip before applying the limit
    * @param limit
    *   the number of elements to project
    * @return
    *   the projection
    */
  def slice(fieldName: String, skip: Int, limit: Int): Projection

  /** Merges 2 sequences of projection operations together. If there are duplicate keys, the last one takes precedence.
    *
    * @param anotherProjection
    *   the projection to be merged with
    * @return
    *   the projection
    */
  def combinedWith(anotherProjection: Projection): Projection

  private[mongo4cats] def toBson: Bson
  private[mongo4cats] def projections: List[Bson]
}

object Projection extends Projection {
  private[mongo4cats] def toBson: Bson            = empty.toBson
  private[mongo4cats] def projections: List[Bson] = empty.projections
  private val empty: Projection                   = ProjectionBuilder(Nil)

  def computed[T](fieldName: String, expression: T): Projection   = empty.computed(fieldName, expression)
  def include(fieldName: String): Projection                      = empty.include(fieldName)
  def include(fieldNames: Seq[String]): Projection                = empty.include(fieldNames)
  def exclude(fieldName: String): Projection                      = empty.exclude(fieldName)
  def exclude(fieldNames: Seq[String]): Projection                = empty.exclude(fieldNames)
  def excludeId: Projection                                       = empty.excludeId
  def elemMatch(fieldName: String): Projection                    = empty.elemMatch(fieldName)
  def elemMatch(fieldName: String, filter: Filter): Projection    = empty.elemMatch(fieldName, filter)
  def meta(fieldName: String, metaFieldName: String): Projection  = empty.meta(fieldName, metaFieldName)
  def metaTextScore(fieldName: String): Projection                = empty.metaTextScore(fieldName)
  def metaSearchScore(fieldName: String): Projection              = empty.metaSearchScore(fieldName)
  def metaSearchHighlights(fieldName: String): Projection         = empty.metaSearchHighlights(fieldName)
  def slice(fieldName: String, limit: Int): Projection            = empty.slice(fieldName, limit)
  def slice(fieldName: String, skip: Int, limit: Int): Projection = empty.slice(fieldName, skip, limit)
  def combinedWith(anotherProjection: Projection): Projection     = empty.combinedWith(anotherProjection)
  def computedSearchMeta(fieldName: String): Projection           = empty.computedSearchMeta(fieldName)
}

final private case class ProjectionBuilder(
    override val projections: List[Bson]
) extends Projection {

  override def combinedWith(anotherProjection: Projection): Projection =
    ProjectionBuilder(anotherProjection.projections ::: projections)

  override private[mongo4cats] def toBson = Projections.fields(projections.reverse: _*)

  override def computed[T](fieldName: String, expression: T): Projection =
    ProjectionBuilder(Projections.computed(fieldName, expression) :: projections)

  override def include(fieldName: String): Projection =
    ProjectionBuilder(Projections.include(fieldName) :: projections)

  override def include(fieldNames: Seq[String]): Projection =
    ProjectionBuilder(Projections.include(fieldNames: _*) :: projections)

  override def exclude(fieldName: String): Projection =
    ProjectionBuilder(Projections.exclude(fieldName) :: projections)

  override def exclude(fieldNames: Seq[String]): Projection =
    ProjectionBuilder(Projections.exclude(fieldNames: _*) :: projections)

  override def excludeId: Projection =
    ProjectionBuilder(Projections.excludeId() :: projections)

  override def elemMatch(fieldName: String): Projection =
    ProjectionBuilder(Projections.elemMatch(fieldName) :: projections)

  override def elemMatch(fieldName: String, filter: Filter): Projection =
    ProjectionBuilder(Projections.elemMatch(fieldName, filter.toBson) :: projections)

  override def meta(fieldName: String, metaFieldName: String): Projection =
    ProjectionBuilder(Projections.meta(fieldName, metaFieldName) :: projections)

  override def metaTextScore(fieldName: String): Projection =
    ProjectionBuilder(Projections.metaTextScore(fieldName) :: projections)

  override def metaSearchScore(fieldName: String): Projection =
    ProjectionBuilder(Projections.metaSearchScore(fieldName) :: projections)

  override def metaSearchHighlights(fieldName: String): Projection =
    ProjectionBuilder(Projections.metaSearchHighlights(fieldName) :: projections)

  override def computedSearchMeta(fieldName: String): Projection =
    ProjectionBuilder(Projections.computedSearchMeta(fieldName) :: projections)

  override def slice(fieldName: String, limit: Int): Projection = {
    val sliceCommand    = Document("$slice" := BsonValue.array(BsonValue.string("$" + fieldName), BsonValue.int(limit)))
    val sliceProjection = Document(fieldName := sliceCommand)
    ProjectionBuilder(sliceProjection.toBsonDocument :: projections)
  }

  override def slice(fieldName: String, skip: Int, limit: Int): Projection = {
    val sliceCommand = Document("$slice" := BsonValue.array(BsonValue.string("$" + fieldName), BsonValue.int(skip), BsonValue.int(limit)))
    val sliceProjection = Document(fieldName := sliceCommand)
    ProjectionBuilder(sliceProjection.toBsonDocument :: projections)
  }
}
