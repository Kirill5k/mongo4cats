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

import com.mongodb.client.model.{
  Aggregates,
  BucketAutoOptions,
  Facet => JFacet,
  Field,
  GeoNearOptions,
  GraphLookupOptions,
  MergeOptions,
  UnwindOptions
}
import mongo4cats.AsJava
import org.bson.conversions.Bson
import com.mongodb.client.model.densify.{DensifyOptions, DensifyRange}
import com.mongodb.client.model.fill.{FillOptions, FillOutputField}
import com.mongodb.client.model.geojson.Point
import com.mongodb.client.model.search.SearchOptions.searchOptions
import com.mongodb.client.model.search.{SearchCollector, SearchOperator, SearchOptions}

trait Aggregate extends AsJava {

  /** Creates a \$bucketAuto pipeline stage
    *
    * @param groupBy
    *   the criteria to group By
    * @param buckets
    *   the number of the buckets
    * @param options
    *   the optional values for the \$bucketAuto stage
    * @return
    *   the \$bucketAuto pipeline stage [[https://docs.mongodb.com/manual/reference/operator/aggregation/bucketAuto/]]
    * @since 3.4
    */
  def bucketAuto[TExpression](
      groupBy: TExpression,
      buckets: Int,
      options: BucketAutoOptions = new BucketAutoOptions()
  ): Aggregate

  /** Creates a \$sample pipeline stage with the specified sample size
    *
    * @param size
    *   the sample size
    * @return
    *   the \$sample pipeline stage [[https://docs.mongodb.com/manual/reference/operator/aggregation/sample/]]
    * @since 3.2
    */
  def sample(size: Int): Aggregate

  /** Creates a \$count pipeline stage using the field name "count" to store the result
    *
    * @return
    *   the \$count pipeline stage [[https://docs.mongodb.com/manual/reference/operator/aggregation/count/]]
    * @since 3.4
    */
  def count: Aggregate

  /** Creates a \$count pipeline stage using the named field to store the result
    *
    * @param field
    *   the field in which to store the count
    * @return
    *   the \$count pipeline stage [[https://docs.mongodb.com/manual/reference/operator/aggregation/count/]]
    * @since 3.4
    */
  def count(field: String): Aggregate

  /** Creates a \$match pipeline stage for the specified filter
    *
    * @param filter
    *   the filter to match
    * @return
    *   the \$match pipeline stage [[https://docs.mongodb.com/manual/reference/operator/aggregation/match/]]
    */
  def matchBy(filter: Filter): Aggregate

  /** Creates a \$project pipeline stage for the specified projection
    *
    * @param projection
    *   the projection
    * @return
    *   the \$project pipeline stage [[https://docs.mongodb.com/manual/reference/operator/aggregation/project/]]
    */
  def project(projection: Projection): Aggregate

  /** Creates a \$sort pipeline stage for the specified sort specification
    *
    * @param sort
    *   the sort specification
    * @return
    *   the \$sort pipeline stage [[https://docs.mongodb.com/manual/reference/operator/aggregation/sort/]]
    */
  def sort(sort: Sort): Aggregate

  /** Creates a \$sortByCount pipeline stage for the specified filter
    *
    * @param filter
    *   the filter specification
    * @return
    *   the \$sortByCount pipeline stage [[https://docs.mongodb.com/manual/reference/operator/aggregation/sortByCount/]]
    * @since 3.4
    */
  def sortByCount[TExpression](filter: TExpression): Aggregate

  /** Creates a \$skip pipeline stage
    *
    * @param n
    *   the number of documents to skip
    * @return
    *   the \$skip pipeline stage [[https://docs.mongodb.com/manual/reference/operator/aggregation/skip/]]
    */
  def skip(n: Int): Aggregate

  /** Creates a \$limit pipeline stage for the specified filter
    *
    * @param n
    *   the limit
    * @return
    *   the \$limit pipeline stage [[https://docs.mongodb.com/manual/reference/operator/aggregation/limi/]]
    */
  def limit(n: Int): Aggregate

  /** Creates a \$lookup pipeline stage, joining the current collection with the one specified in from using equality match between the
    * local field and the foreign field
    *
    * @param from
    *   the name of the collection in the same database to perform the join with.
    * @param localField
    *   the field from the local collection to match values against.
    * @param foreignField
    *   the field in the from collection to match values against.
    * @param as
    *   the name of the new array field to add to the input documents.
    * @return
    *   the \$lookup pipeline stage [[https://docs.mongodb.com/manual/reference/operator/aggregation/lookup/]]
    * @since 3.2
    */
  def lookup(from: String, localField: String, foreignField: String, as: String): Aggregate

  /** Creates a \$group pipeline stage for the specified filter
    *
    * @param id
    *   the id expression for the group
    * @param fieldAccumulators
    *   zero or more field accumulator pairs
    * @return
    *   the \$group pipeline stage [[https://docs.mongodb.com/manual/reference/operator/aggregation/group/]]
    */
  def group[TExpression](id: TExpression, fieldAccumulators: Accumulator): Aggregate

  /** Creates a \$unwind pipeline stage for the specified field name, which must be prefixed by a '\$' sign.
    *
    * @param fieldName
    *   the field name, prefixed by a '\$' sign
    * @param unwindOptions
    *   options for the unwind pipeline stage
    * @return
    *   the \$unwind pipeline stage [[https://docs.mongodb.com/manual/reference/operator/aggregation/unwind/]]
    * @since 3.2
    */
  def unwind(fieldName: String, unwindOptions: UnwindOptions = new UnwindOptions()): Aggregate

  /** Creates a \$out pipeline stage that writes into the specified collection
    *
    * @param collectionName
    *   the collection name
    * @return
    *   the \$out pipeline stage [[https://docs.mongodb.com/manual/reference/operator/aggregation/out/]]
    */
  def out(collectionName: String): Aggregate

  /** Creates a \$out pipeline stage that supports outputting to a different database.
    *
    * @param databaseName
    *   the database name
    * @param collectionName
    *   the collection name
    * @return
    *   the \$out pipeline stage [[https://docs.mongodb.com/manual/reference/operator/aggregation/out/]]
    * @since 4.1
    */
  def out(databaseName: String, collectionName: String): Aggregate

  /** Creates a \$merge pipeline stage that merges into the specified collection using the specified options.
    *
    * @param collectionName
    *   the name of the collection to merge into
    * @param options
    *   the merge options
    * @return
    *   the \$merge pipeline stage [[https://docs.mongodb.com/manual/reference/operator/aggregation/merge/]]
    * @since 3.11
    */
  def merge(collectionName: String, options: MergeOptions = new MergeOptions()): Aggregate

  /** Creates a \$replaceRoot pipeline stage
    *
    * <p>With \$replaceWith, you can promote an embedded document to the top-level. You can also specify a new document as the
    * replacement.</p>
    *
    * @param value
    *   the new root value
    * @return
    *   the \$replaceRoot pipeline stage [[https://docs.mongodb.com/manual/reference/operator/aggregation/replaceWith/]]
    * @since 3.11
    */
  def replaceWith[TExpression](value: TExpression): Aggregate

  /** Creates a \$lookup pipeline stage, joining the current collection with the one specified in from using the given pipeline
    *
    * @param from
    *   the name of the collection in the same database to perform the join with.
    * @param pipeline
    *   the pipeline to run on the joined collection.
    * @param as
    *   the name of the new array field to add to the input documents.
    * @return
    *   the \$lookup pipeline stage [[https://docs.mongodb.com/manual/reference/operator/aggregation/lookup/]]
    * @since 3.7
    */
  def lookup(from: String, pipeline: Aggregate, as: String): Aggregate

  /** Creates a \$addFields pipeline stage
    *
    * <p>With \$addFields, you can adds the new fields the document.</p>
    *
    * @param name
    *   the name of the new field
    * @param value
    *   the value of the new field
    * @return
    *   the \$addFields pipeline stage [[https://www.mongodb.com/docs/manual/reference/operator/aggregation/addFields/]]
    * @since 3.4
    */
  def addFields[TExpression](fields: List[(String, TExpression)]): Aggregate
  def addFields[TExpression](fields: (String, TExpression)*): Aggregate = addFields(fields.toList)

  /** Creates a \$set pipeline stage for the specified projection
    *
    * @param fields
    *   the fields to add
    * @return
    *   the \$set pipeline stage
    * @see
    *   Projections
    * @since 4.3
    */
  def set[TExpression](fields: List[(String, TExpression)]): Aggregate
  def set[TExpression](fields: (String, TExpression)*): Aggregate = set(fields.toList)

  /** Creates a graphLookup pipeline stage for the specified filter
    *
    * @param from
    *   the collection to query
    * @param startWith
    *   the expression to start the graph lookup with
    * @param connectFromField
    *   the from field
    * @param connectToField
    *   the to field
    * @param as
    *   name of field in output document
    * @param options
    *   optional values for the graphLookup
    * @return
    *   the \$graphLookup pipeline stage [[https://docs.mongodb.com/manual/reference/operator/aggregation/graphLookup/]]
    * @since 3.4
    */
  def graphLookup[TExpression](
      from: String,
      startWith: TExpression,
      connectFromField: String,
      connectToField: String,
      as: String,
      options: GraphLookupOptions = new GraphLookupOptions()
  ): Aggregate

  /** Creates a facet pipeline stage
    *
    * @param facets
    *   the facets to use
    * @return
    *   the \$facets pipeline stage [[https://docs.mongodb.com/manual/reference/operator/aggregation/facet/]]
    * @since 3.4
    */
  def facet(facets: List[Aggregate.Facet]): Aggregate

  def facet(facets: Aggregate.Facet*): Aggregate = facet(facets.toList)

  /** Creates a \$unionWith pipeline stage.
    *
    * @param collection
    *   the name of the collection in the same database to perform the union with.
    * @param pipeline
    *   the pipeline to run on the union.
    * @return
    *   the \$unionWith pipeline stage [[https://docs.mongodb.com/manual/reference/operator/aggregation/unionWith/]]
    * @since 4.1
    */
  def unionWith(collection: String, pipeline: Aggregate): Aggregate

  /** Merges 2 aggregation pipelines together.
    *
    * @param anotherAggregate
    *   the aggregate to be merged with
    * @return
    *   the aggregate pipeline
    */
  def combinedWith(anotherAggregate: Aggregate): Aggregate

  /** Creates an \$unset pipeline stage
    *
    * <p> With \$unset, you can removes/excludes fields from documents
    *
    * @param fields
    *   the fields to exclude. May use dot notation.
    * @return
    *   the \$unset aggregate stage [[https://www.mongodb.com/docs/manual/reference/operator/aggregation/unset/]]
    * @since 4.8
    */
  def unset(fields: List[String]): Aggregate
  def unset(fields: String*): Aggregate = unset(fields.toList)

  /** Creates a \$densify pipeline stage, which adds documents to a sequence of documents where certain values in the field are missing.
    *
    * @param field
    *   The field to densify.
    * @param range
    *   The range.
    * @param options
    *   The densify options.
    * @return
    *   The requested pipeline stage.
    * @since 4.7
    */
  def densify(field: String, range: DensifyRange, options: DensifyOptions): Aggregate
  def densify(field: String, range: DensifyRange): Aggregate = densify(field, range, DensifyOptions.densifyOptions())

  /** Creates a \$fill pipeline stage, which assigns values to fields when they are Null or missing.
    *
    * @param options
    *   The fill options.
    * @param outputs
    *   The FillOutputField.
    * @since 4.7
    */
  def fill(options: FillOptions, outputs: List[FillOutputField]): Aggregate
  def fill(options: FillOptions, output: FillOutputField, outputs: FillOutputField*): Aggregate =
    fill(options, output :: outputs.toList)

  /** Creates a \$geoNear pipeline stage that outputs documents in order of nearest to farthest from a specified point.
    *
    * @param point
    *   The point for which to find the closest documents.
    * @param distanceField
    *   The output field that contains the calculated distance. To specify a field within an embedded document, use dot notation.
    * @param options
    *   GeoNearOptions
    * @since 4.8
    */
  def geoNear(point: Point, distanceField: String, options: GeoNearOptions): Aggregate
  def geoNear(point: Point, distanceField: String): Aggregate = geoNear(point, distanceField, GeoNearOptions.geoNearOptions())

  /** Creates a \$search pipeline stage supported by MongoDB Atlas. You may use \$meta: "searchScore", e.g., via
    * Projection.metaSearchScore(String), to extract the relevance score assigned to each found document.
    * @param operator
    *   A search operator.
    * @param options
    *   Optional \$search pipeline stage fields.
    * @since 4.7
    */
  def search(operator: SearchOperator, options: SearchOptions): Aggregate
  def search(operator: SearchOperator): Aggregate = search(operator, SearchOptions.searchOptions())

  /** Creates a \$search pipeline stage supported by MongoDB Atlas. You may use \$meta: "searchScore", e.g., via
    * Projection.metaSearchScore(String), to extract the relevance score assigned to each found document.
    * @param collector
    *   A search collector.
    * @param options
    *   Optional \$search pipeline stage fields.
    * @return
    *   The \$search pipeline stage.
    * @since 4.7
    */
  def search(collector: SearchCollector, options: SearchOptions): Aggregate
  def search(collector: SearchCollector): Aggregate = search(collector, SearchOptions.searchOptions())

  private[mongo4cats] def aggregates: List[Bson]
  private[mongo4cats] def toBson: java.util.List[Bson]
}

object Aggregate {
  final case class Facet(name: String, pipeline: Aggregate) {
    private[operations] def toJava: JFacet = new JFacet(name, pipeline.toBson)
  }

  private[mongo4cats] val empty: Aggregate = AggregateBuilder(Nil)

  def bucketAuto[TExpression](
      groupBy: TExpression,
      buckets: Int,
      options: BucketAutoOptions = new BucketAutoOptions()
  ): Aggregate = empty.bucketAuto(groupBy, buckets, options)

  def sample(size: Int): Aggregate                             = empty.sample(size)
  def count: Aggregate                                         = empty.count
  def count(field: String): Aggregate                          = empty.count(field)
  def matchBy(filter: Filter): Aggregate                       = empty.matchBy(filter)
  def project(projection: Projection): Aggregate               = empty.project(projection)
  def sort(sort: Sort): Aggregate                              = empty.sort(sort)
  def sortByCount[TExpression](filter: TExpression): Aggregate = empty.sortByCount(filter)
  def skip(n: Int): Aggregate                                  = empty.skip(n)
  def limit(n: Int): Aggregate                                 = empty.limit(n)

  def lookup(from: String, localField: String, foreignField: String, as: String): Aggregate =
    empty.lookup(from, localField, foreignField, as)

  def group[TExpression](id: TExpression, fieldAccumulators: Accumulator): Aggregate     = empty.group(id, fieldAccumulators)
  def unwind(fieldName: String, options: UnwindOptions = new UnwindOptions()): Aggregate = empty.unwind(fieldName, options)

  def facet(facets: List[Aggregate.Facet]): Aggregate                                      = empty.facet(facets)
  def facet(facets: Aggregate.Facet*): Aggregate                                           = empty.facet(facets.toList)
  def out(collectionName: String): Aggregate                                               = empty.out(collectionName)
  def out(databaseName: String, collectionName: String): Aggregate                         = empty.out(databaseName, collectionName)
  def merge(collectionName: String, options: MergeOptions = new MergeOptions()): Aggregate = empty.merge(collectionName, options)
  def replaceWith[TExpression](value: TExpression): Aggregate                              = empty.replaceWith(value)
  def addFields[TExpression](fields: (String, TExpression)*): Aggregate                    = empty.addFields(fields.toList)
  def addFields[TExpression](fields: List[(String, TExpression)]): Aggregate               = empty.addFields(fields)
  def unset(fields: String*): Aggregate                                                    = empty.unset(fields.toList)
  def unset(fields: List[String]): Aggregate                                               = empty.unset(fields)
  def set[TExpression](fields: List[(String, TExpression)]): Aggregate                     = empty.set(fields)
  def set[TExpression](fields: (String, TExpression)*): Aggregate                          = empty.set(fields.toList)
  def lookup(from: String, pipeline: Aggregate, as: String): Aggregate                     = empty.lookup(from, pipeline, as)
  def densify(field: String, range: DensifyRange, options: DensifyOptions): Aggregate      = empty.densify(field, range, options)
  def densify(field: String, range: DensifyRange): Aggregate                               = empty.densify(field, range)
  def fill(options: FillOptions, outputs: List[FillOutputField]): Aggregate                = empty.fill(options, outputs)
  def fill(options: FillOptions, output: FillOutputField, outputs: FillOutputField*): Aggregate =
    empty.fill(options, output :: outputs.toList)

  def geoNear(point: Point, distanceField: String, options: GeoNearOptions): Aggregate = empty.geoNear(point, distanceField, options)
  def geoNear(point: Point, distanceField: String): Aggregate                          = empty.geoNear(point, distanceField)
  def search(operator: SearchOperator, options: SearchOptions): Aggregate              = empty.search(operator, options)
  def search(operator: SearchOperator): Aggregate                                      = empty.search(operator)
  def search(collector: SearchCollector, options: SearchOptions): Aggregate            = empty.search(collector, options)
  def search(collector: SearchCollector): Aggregate                                    = empty.search(collector)

  def graphLookup[TExpression](
      from: String,
      startWith: TExpression,
      connectFromField: String,
      connectToField: String,
      as: String,
      options: GraphLookupOptions = new GraphLookupOptions()
  ): Aggregate = empty.graphLookup(from, startWith, connectFromField, connectToField, as, options)

  def unionWith(collection: String, pipeline: Aggregate): Aggregate = empty.unionWith(collection, pipeline)
}

final private case class AggregateBuilder(
    override val aggregates: List[Bson]
) extends Aggregate with AsJava {

  private def toJavaField[T](fields: List[(String, T)]): List[Field[?]] =
    fields.map { case (name, value) => new Field(name, value) }

  def bucketAuto[TExpression](
      groupBy: TExpression,
      buckets: Int,
      options: BucketAutoOptions = new BucketAutoOptions()
  ): Aggregate = AggregateBuilder(Aggregates.bucketAuto(groupBy, buckets, options) :: aggregates)

  def sample(size: Int): Aggregate = AggregateBuilder(Aggregates.sample(size) :: aggregates)

  def count: Aggregate = AggregateBuilder(Aggregates.count() :: aggregates)

  def count(field: String): Aggregate = AggregateBuilder(Aggregates.count(field) :: aggregates)

  def matchBy(filter: Filter): Aggregate = AggregateBuilder(Aggregates.`match`(filter.toBson) :: aggregates)

  def project(projection: Projection): Aggregate = AggregateBuilder(Aggregates.project(projection.toBson) :: aggregates)

  def sort(sort: Sort): Aggregate = AggregateBuilder(Aggregates.sort(sort.toBson) :: aggregates)

  def sortByCount[TExpression](filter: TExpression): Aggregate = AggregateBuilder(Aggregates.sortByCount(filter) :: aggregates)

  def skip(n: Int): Aggregate = AggregateBuilder(Aggregates.skip(n) :: aggregates)

  def limit(n: Int): Aggregate = AggregateBuilder(Aggregates.limit(n) :: aggregates)

  def lookup(from: String, localField: String, foreignField: String, as: String): Aggregate =
    AggregateBuilder(Aggregates.lookup(from, localField, foreignField, as) :: aggregates)

  def group[TExpression](id: TExpression, fieldAccumulators: Accumulator): Aggregate =
    AggregateBuilder(Aggregates.group(id, fieldAccumulators.toBson) :: aggregates)

  def unwind(fieldName: String, unwindOptions: UnwindOptions = new UnwindOptions()): Aggregate =
    AggregateBuilder(Aggregates.unwind(fieldName, unwindOptions) :: aggregates)

  def out(collectionName: String): Aggregate = AggregateBuilder(Aggregates.out(collectionName) :: aggregates)

  def out(databaseName: String, collectionName: String): Aggregate =
    AggregateBuilder(Aggregates.out(databaseName, collectionName) :: aggregates)

  def merge(collectionName: String, options: MergeOptions = new MergeOptions()): Aggregate =
    AggregateBuilder(Aggregates.merge(collectionName, options) :: aggregates)

  def replaceWith[TExpression](value: TExpression): Aggregate = AggregateBuilder(Aggregates.replaceWith(value) :: aggregates)

  def addFields[TExpression](fields: List[(String, TExpression)]): Aggregate =
    AggregateBuilder(Aggregates.addFields(asJava(toJavaField(fields))) :: aggregates)

  def set[TExpression](fields: List[(String, TExpression)]): Aggregate =
    AggregateBuilder(Aggregates.set(asJava(toJavaField(fields))) :: aggregates)

  def unset(fields: List[String]): Aggregate =
    AggregateBuilder(Aggregates.unset(asJava(fields)) :: aggregates)

  def lookup(from: String, pipeline: Aggregate, as: String): Aggregate =
    AggregateBuilder(Aggregates.lookup(from, pipeline.toBson, as) :: aggregates)

  def graphLookup[TExpression](
      from: String,
      startWith: TExpression,
      connectFromField: String,
      connectToField: String,
      as: String,
      options: GraphLookupOptions = new GraphLookupOptions()
  ): Aggregate = AggregateBuilder(Aggregates.graphLookup(from, startWith, connectFromField, connectToField, as, options) :: aggregates)

  def facet(facets: List[Aggregate.Facet]): Aggregate = AggregateBuilder(Aggregates.facet(asJava(facets.map(_.toJava))) :: aggregates)

  def unionWith(collection: String, pipeline: Aggregate): Aggregate =
    AggregateBuilder(Aggregates.unionWith(collection, pipeline.toBson) :: aggregates)

  def densify(field: String, range: DensifyRange, options: DensifyOptions): Aggregate =
    AggregateBuilder(Aggregates.densify(field, range, options) :: aggregates)

  def fill(options: FillOptions, outputs: List[FillOutputField]): Aggregate =
    AggregateBuilder(Aggregates.fill(options, asJava(outputs)) :: aggregates)

  def geoNear(point: Point, distanceField: String, options: GeoNearOptions): Aggregate =
    AggregateBuilder(Aggregates.geoNear(point, distanceField, options) :: aggregates)

  def search(operator: SearchOperator, options: SearchOptions): Aggregate =
    AggregateBuilder(Aggregates.search(operator, options) :: aggregates)

  def search(collector: SearchCollector, options: SearchOptions): Aggregate =
    AggregateBuilder(Aggregates.search(collector, options) :: aggregates)

  override def combinedWith(anotherAggregate: Aggregate): Aggregate = AggregateBuilder(anotherAggregate.aggregates ::: aggregates)

  override private[mongo4cats] def toBson: java.util.List[Bson] = asJava(aggregates.reverse)
}
