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
import mongo4cats.AsJava
import mongo4cats.bson.{BsonValue, Document}
import mongo4cats.bson.syntax._
import org.bson.conversions.Bson

class Projection(
    private val projections: List[Bson]
) extends AnyRef with Serializable with AsJava {

  def this() = this(Nil)

  private def withProjection(projection: Bson): Projection =
    new Projection(projection :: projections)

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
  def computed[T](fieldName: String, expression: T): Projection =
    withProjection(Projections.computed(fieldName, expression))

  /** Creates a projection that includes all of the given fields.
    *
    * @param fieldName
    *   the field name
    * @return
    *   the projection
    */
  def include(fieldName: String): Projection =
    withProjection(Projections.include(fieldName))

  /** Creates a projection that includes all of the given fields.
    *
    * @param fieldNames
    *   the field names
    * @return
    *   the projection
    */
  def include(fieldNames: Seq[String]): Projection =
    withProjection(Projections.include(fieldNames: _*))

  /** Creates a projection that excludes all of the given fields.
    *
    * @param fieldName
    *   the field name
    * @return
    *   the projection
    */
  def exclude(fieldName: String): Projection =
    withProjection(Projections.exclude(fieldName))

  /** Creates a projection that excludes all of the given fields.
    *
    * @param fieldNames
    *   the field names
    * @return
    *   the projection
    */
  def exclude(fieldNames: Seq[String]): Projection =
    withProjection(Projections.exclude(fieldNames: _*))

  /** Creates a projection that excludes the _id field. This suppresses the automatic inclusion of _id that is the default, even when other
    * fields are explicitly included.
    *
    * @return
    *   the projection
    */
  def excludeId: Projection =
    withProjection(Projections.excludeId())

  /** Creates a projection that includes for the given field only the first element of an array that matches the query filter. This is
    * referred to as the positional \$ operator.
    *
    * @param fieldName
    *   the field name whose value is the array
    * @return
    *   the projection
    */
  def elemMatch(fieldName: String): Projection =
    withProjection(Projections.elemMatch(fieldName))

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
  def elemMatch(fieldName: String, filter: Filter): Projection =
    withProjection(Projections.elemMatch(fieldName, filter.toBson))

  /** Creates a \$meta projection to the given field name for the given meta field name.
    *
    * @param fieldName
    *   the field name
    * @param metaFieldName
    *   the meta field name
    * @return
    *   the projection
    */
  def meta(fieldName: String, metaFieldName: String): Projection =
    withProjection(Projections.meta(fieldName, metaFieldName))

  /** Creates a projection to the given field name of the textScore, for use with text queries.
    *
    * @param fieldName
    *   the field name
    * @return
    *   the projection
    */
  def metaTextScore(fieldName: String): Projection =
    withProjection(Projections.metaTextScore(fieldName))

  /** Creates a projection to the given field name of the searchScore, for use with Aggregate.search(SearchOperator,SearchOptions) /
    * Aggregate.search(SearchCollector,SearchOptions). Calling this method is equivalent to calling meta(String,String) with "searchScore"
    * as the second argument.
    *
    * @param fieldName
    *   the field name
    * @return
    *   the projection
    */
  def metaSearchScore(fieldName: String): Projection =
    withProjection(Projections.metaSearchScore(fieldName))

  /** Creates a projection to the given field name of the searchHighlights, for use with Aggregate.search(SearchOperator,SearchOptions) /
    * Aggregates.search(SearchCollector,SearchOptions). Calling this method is equivalent to calling meta String,String) with
    * "searchHighlights" as the second argument.
    *
    * @param fieldName
    *   the field name
    * @return
    *   the projection
    */
  def metaSearchHighlights(fieldName: String): Projection =
    withProjection(Projections.metaSearchHighlights(fieldName))

  /** Creates a projection of a field whose value is equal to the \$\$SEARCH_META variable, for use with
    * Aggregate.search(SearchOperator,SearchOptions) / Aggregate.search(SearchCollector,SearchOptions). Calling this method is equivalent to
    * calling computed(String,Object) with "\$\$SEARCH_META" as the second argument.
    *
    * @param fieldName
    *   the field name
    * @return
    *   the projection
    */
  def computedSearchMeta(fieldName: String): Projection =
    withProjection(Projections.computedSearchMeta(fieldName))

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
  def slice(fieldName: String, skip: Int, limit: Int): Projection = {
    val sliceCommand = Document("$slice" := BsonValue.array(BsonValue.string("$" + fieldName), BsonValue.int(skip), BsonValue.int(limit)))
    val sliceProjection = Document(fieldName := sliceCommand)
    withProjection(sliceProjection.toBsonDocument)
  }

  /** Creates a projection to the given field name of a slice of the array value of that field.
    *
    * @param fieldName
    *   the field name
    * @param limit
    *   the number of elements to project.
    * @return
    *   the projection
    */
  def slice(fieldName: String, limit: Int): Projection =
    slice(fieldName, 0, limit)

  /** Creates a projection to the given field name of the vectorSearchScore, for use with
    * Aggregate.vectorSearch(FieldSearchPath,Seq,String,Long,Long,VectorSearchOptions). Calling this method is equivalent to calling
    * meta(String,String) with "vectorSearchScore" as the second argument.
    *
    * @param fieldName
    *   the field name
    * @return
    *   the projection
    */
  def metaVectorSearchScore(fieldName: String): Projection =
    withProjection(Projections.metaVectorSearchScore(fieldName))

  /** Merges 2 sequences of projection operations together. If there are duplicate keys, the last one takes precedence.
    *
    * @param anotherProjection
    *   the projection to be merged with
    * @return
    *   the projection
    */
  def combinedWith(anotherProjection: Projection): Projection =
    new Projection(anotherProjection.projections ::: projections)

  private[mongo4cats] def toBson: Bson = Projections.fields(projections.reverse: _*)

  override def toString: String = projections.reverse.mkString("[", ",", "]")
  override def hashCode(): Int  = projections.hashCode()
  override def equals(other: Any): Boolean =
    Option(other) match {
      case Some(proj: Projection) => proj.projections == projections
      case _                      => false
    }
}

object Projection extends Projection {}
