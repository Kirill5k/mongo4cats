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
  BucketOptions,
  Facet => JFacet,
  Field,
  GeoNearOptions,
  GraphLookupOptions,
  MergeOptions,
  UnwindOptions,
  WindowOutputField
}
import mongo4cats.AsJava
import org.bson.conversions.Bson
import com.mongodb.client.model.densify.{DensifyOptions, DensifyRange}
import com.mongodb.client.model.fill.{FillOptions, FillOutputField}
import com.mongodb.client.model.geojson.Point
import com.mongodb.client.model.search.{FieldSearchPath, SearchCollector, SearchOperator, SearchOptions, VectorSearchOptions}

class Aggregate(
    private val aggregates: List[Bson]
) extends AsJava with Serializable {

  def this() = this(Nil)

  private def withAggregate(aggregate: Bson): Aggregate =
    new Aggregate(aggregate :: aggregates)

  private def toJavaDouble(double: Double): java.lang.Double =
    java.lang.Double.valueOf(double)

  private def toJavaField[T](fields: List[(String, T)]): List[Field[?]] =
    fields.map { case (name, value) => new Field(name, value) }

  /** Creates a \$bucket pipeline stage
    *
    * @param groupBy
    *   the criteria to group By
    * @param boundaries
    *   the boundaries of the buckets
    * @param options
    *   the optional values for the \$bucket stage
    * @return
    *   Aggregate \$bucket pipeline stage [[https://docs.mongodb.com/manual/reference/operator/aggregation/bucket/]]
    */
  def bucket[TExpression](
      groupBy: TExpression,
      boundaries: Seq[Double],
      options: BucketOptions = new BucketOptions()
  ): Aggregate =
    withAggregate(Aggregates.bucket(groupBy, asJava(boundaries.map(toJavaDouble)), options))

  /** Creates a \$bucketAuto pipeline stage.
    *
    * @param groupBy
    *   the criteria to group By
    * @param buckets
    *   the number of the buckets
    * @param options
    *   the optional values for the \$bucketAuto stage
    * @return
    *   Aggregate with \$bucketAuto pipeline stage [[https://docs.mongodb.com/manual/reference/operator/aggregation/bucketAuto/]]
    */
  def bucketAuto[TExpression](
      groupBy: TExpression,
      buckets: Int,
      options: BucketAutoOptions = new BucketAutoOptions()
  ): Aggregate =
    withAggregate(Aggregates.bucketAuto(groupBy, buckets, options))

  /** Creates a \$sample pipeline stage with the specified sample size.
    *
    * @param size
    *   the sample size
    * @return
    *   Aggregate with \$sample pipeline stage [[https://docs.mongodb.com/manual/reference/operator/aggregation/sample/]]
    */
  def sample(size: Int): Aggregate =
    withAggregate(Aggregates.sample(size))

  /** Creates a \$count pipeline stage using the field name "count" to store the result.
    *
    * @return
    *   Aggregate with \$count pipeline stage [[https://docs.mongodb.com/manual/reference/operator/aggregation/count/]]
    */
  def count: Aggregate =
    withAggregate(Aggregates.count())

  /** Creates a \$count pipeline stage using the named field to store the result.
    *
    * @param field
    *   the field in which to store the count
    * @return
    *   Aggregate with \$count pipeline stage [[https://docs.mongodb.com/manual/reference/operator/aggregation/count/]]
    */
  def count(field: String): Aggregate =
    withAggregate(Aggregates.count(field))

  /** Creates a \$match pipeline stage for the specified filter.
    *
    * @param filter
    *   the filter to match
    * @return
    *   Aggregate with \$match pipeline stage [[https://docs.mongodb.com/manual/reference/operator/aggregation/match/]]
    */
  def matchBy(filter: Filter): Aggregate =
    withAggregate(Aggregates.`match`(filter.toBson))

  /** Creates a \$project pipeline stage for the specified projection.
    *
    * @param projection
    *   the projection
    * @return
    *   Aggregate with \$project pipeline stage [[https://docs.mongodb.com/manual/reference/operator/aggregation/project/]]
    */
  def project(projection: Projection): Aggregate =
    withAggregate(Aggregates.project(projection.toBson))

  /** Creates a \$sort pipeline stage for the specified sort specification.
    *
    * @param sort
    *   the sort specification
    * @return
    *   Aggregate with \$sort pipeline stage [[https://docs.mongodb.com/manual/reference/operator/aggregation/sort/]]
    */
  def sort(sort: Sort): Aggregate =
    withAggregate(Aggregates.sort(sort.toBson))

  /** Creates a \$sortByCount pipeline stage for the specified filter.
    *
    * @param filter
    *   the filter specification
    * @return
    *   Aggregate with \$sortByCount pipeline stage [[https://docs.mongodb.com/manual/reference/operator/aggregation/sortByCount/]]
    */
  def sortByCount[TExpression](filter: TExpression): Aggregate =
    withAggregate(Aggregates.sortByCount(filter))

  /** Creates a \$skip pipeline stage.
    *
    * @param n
    *   the number of documents to skip
    * @return
    *   Aggregate with \$skip pipeline stage [[https://docs.mongodb.com/manual/reference/operator/aggregation/skip/]]
    */
  def skip(n: Int): Aggregate =
    withAggregate(Aggregates.skip(n))

  /** Creates a \$limit pipeline stage for the specified filter
    *
    * @param n
    *   the limit
    * @return
    *   Aggregate with \$limit pipeline stage [[https://docs.mongodb.com/manual/reference/operator/aggregation/limi/]]
    */
  def limit(n: Int): Aggregate =
    withAggregate(Aggregates.limit(n))

  /** Creates a \$lookup pipeline stage, joining the current collection with the one specified in from using equality match between the
    * local field and the foreign field.
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
    *   Aggregate with \$lookup pipeline stage [[https://docs.mongodb.com/manual/reference/operator/aggregation/lookup/]]
    */
  def lookup(from: String, localField: String, foreignField: String, as: String): Aggregate =
    withAggregate(Aggregates.lookup(from, localField, foreignField, as))

  /** Creates a \$group pipeline stage for the specified filter.
    *
    * @param id
    *   the id expression for the group
    * @param fieldAccumulators
    *   zero or more field accumulator pairs
    * @return
    *   Aggregate with \$group pipeline stage [[https://docs.mongodb.com/manual/reference/operator/aggregation/group/]]
    */
  def group[TExpression](id: TExpression, fieldAccumulators: Accumulator): Aggregate =
    withAggregate(Aggregates.group(id, fieldAccumulators.toBson))

  /** Creates a \$unwind pipeline stage for the specified field name, which must be prefixed by a '\$' sign.
    *
    * @param fieldName
    *   the field name, prefixed by a '\$' sign
    * @param unwindOptions
    *   options for the unwind pipeline stage
    * @return
    *   Aggregate with \$unwind pipeline stage [[https://docs.mongodb.com/manual/reference/operator/aggregation/unwind/]]
    */
  def unwind(fieldName: String, unwindOptions: UnwindOptions = new UnwindOptions()): Aggregate =
    withAggregate(Aggregates.unwind(fieldName, unwindOptions))

  /** Creates a \$out pipeline stage that writes into the specified collection
    *
    * @param collectionName
    *   the collection name
    * @return
    *   Aggregate with \$out pipeline stage [[https://docs.mongodb.com/manual/reference/operator/aggregation/out/]]
    */
  def out(collectionName: String): Aggregate =
    withAggregate(Aggregates.out(collectionName))

  /** Creates a \$out pipeline stage that supports outputting to a different database.
    *
    * @param databaseName
    *   the database name
    * @param collectionName
    *   the collection name
    * @return
    *   Aggregate with \$out pipeline stage [[https://docs.mongodb.com/manual/reference/operator/aggregation/out/]]
    */
  def out(databaseName: String, collectionName: String): Aggregate =
    withAggregate(Aggregates.out(databaseName, collectionName))

  /** Creates a \$merge pipeline stage that merges into the specified collection using the specified options.
    *
    * @param collectionName
    *   the name of the collection to merge into
    * @param options
    *   the merge options
    * @return
    *   Aggregate with \$merge pipeline stage [[https://docs.mongodb.com/manual/reference/operator/aggregation/merge/]]
    */
  def merge(collectionName: String, options: MergeOptions = new MergeOptions()): Aggregate =
    withAggregate(Aggregates.merge(collectionName, options))

  /** Creates a \$replaceRoot pipeline stage. With \$replaceWith you can promote an embedded document to the top-level. You can also specify
    * a new document as the replacement.
    *
    * @param value
    *   the new root value
    * @return
    *   Aggregate with \$replaceRoot pipeline stage [[https://docs.mongodb.com/manual/reference/operator/aggregation/replaceWith/]]
    */
  def replaceWith[TExpression](value: TExpression): Aggregate =
    withAggregate(Aggregates.replaceWith(value))

  /** Creates a \$lookup pipeline stage, joining the current collection with the one specified in from using the given pipeline.
    *
    * @param from
    *   the name of the collection in the same database to perform the join with.
    * @param pipeline
    *   the pipeline to run on the joined collection.
    * @param as
    *   the name of the new array field to add to the input documents.
    * @return
    *   Aggregate with \$lookup pipeline stage [[https://docs.mongodb.com/manual/reference/operator/aggregation/lookup/]]
    */
  def lookup(from: String, pipeline: Aggregate, as: String): Aggregate =
    withAggregate(Aggregates.lookup(from, pipeline.toBson, as))

  /** Creates a \$addFields pipeline stage. With \$addFields you can add new fields the document.
    *
    * @param name
    *   the name of the new field
    * @param value
    *   the value of the new field
    * @return
    *   Aggregate with \$addFields pipeline stage [[https://www.mongodb.com/docs/manual/reference/operator/aggregation/addFields/]]
    */
  def addFields[TExpression](fields: List[(String, TExpression)]): Aggregate =
    withAggregate(Aggregates.addFields(asJava(toJavaField(fields))))

  def addFields[TExpression](field: (String, TExpression), fields: (String, TExpression)*): Aggregate =
    addFields(field :: fields.toList)

  /** Creates a \$set pipeline stage for the specified projection.
    *
    * @param fields
    *   the fields to add
    * @return
    *   Aggregate with \$set pipeline stage [[https://docs.mongodb.com/manual/reference/operator/aggregation/set/]]
    */
  def set[TExpression](fields: List[(String, TExpression)]): Aggregate =
    withAggregate(Aggregates.set(asJava(toJavaField(fields))))

  def set[TExpression](field: (String, TExpression), fields: (String, TExpression)*): Aggregate =
    set(field :: fields.toList)

  /** Creates a graphLookup pipeline stage for the specified filter.
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
    *   Aggregate with \$graphLookup pipeline stage [[https://docs.mongodb.com/manual/reference/operator/aggregation/graphLookup/]]
    */
  def graphLookup[TExpression](
      from: String,
      startWith: TExpression,
      connectFromField: String,
      connectToField: String,
      as: String,
      options: GraphLookupOptions = new GraphLookupOptions()
  ): Aggregate =
    withAggregate(Aggregates.graphLookup(from, startWith, connectFromField, connectToField, as, options))

  /** Creates a \$vectorSearch pipeline stage supported by MongoDB Atlas. You may use the \$meta: "vectorSearchScore" expression, e.g., via
    * Projection.metaVectorSearchScore(String), to extract the relevance score assigned to each found document.
    *
    * @param queryVector
    *   The query vector. The number of dimensions must match that of the index.
    * @param path
    *   The field to be searched.
    * @param index
    *   The name of the index to use.
    * @param limit
    *   The limit on the number of documents produced by the pipeline stage.
    * @param options
    *   Optional \$vectorSearch pipeline stage fields.
    * @return
    *   The Aggregate with \$vectorSearch pipeline stage [[https://docs.mongodb.com/manual/reference/operator/aggregation/vectorSearch/]]
    */
  def vectorSearch(
      path: FieldSearchPath,
      queryVector: Seq[Double],
      index: String,
      limit: Long,
      options: VectorSearchOptions = VectorSearchOptions.exactVectorSearchOptions()
  ): Aggregate =
    withAggregate(Aggregates.vectorSearch(path, asJava(queryVector.map(toJavaDouble)), index, limit, options))

  /** Creates a facet pipeline stage.
    *
    * @param facets
    *   the facets to use
    * @return
    *   Aggregate with \$facets pipeline stage [[https://docs.mongodb.com/manual/reference/operator/aggregation/facet/]]
    */
  def facet(facets: List[Aggregate.Facet]): Aggregate =
    withAggregate(Aggregates.facet(asJava(facets.map(_.toJava))))

  def facet(facets: Aggregate.Facet*): Aggregate =
    facet(facets.toList)

  /** Creates a \$unionWith pipeline stage.
    *
    * @param collection
    *   the name of the collection in the same database to perform the union with.
    * @param pipeline
    *   the pipeline to run on the union.
    * @return
    *   Aggregate with \$unionWith pipeline stage [[https://docs.mongodb.com/manual/reference/operator/aggregation/unionWith/]]
    */
  def unionWith(collection: String, pipeline: Aggregate): Aggregate =
    withAggregate(Aggregates.unionWith(collection, pipeline.toBson))

  /** Creates an \$unset pipeline stage. With \$unset you can removes/excludes fields from documents.
    *
    * @param fields
    *   the fields to exclude. May use dot notation.
    * @return
    *   Aggregate with \$unset aggregate stage [[https://www.mongodb.com/docs/manual/reference/operator/aggregation/unset/]]
    */
  def unset(fields: List[String]): Aggregate =
    withAggregate(Aggregates.unset(asJava(fields)))

  def unset(fields: String*): Aggregate =
    unset(fields.toList)

  /** Creates a \$densify pipeline stage, which adds documents to a sequence of documents where certain values in the field are missing.
    *
    * @param field
    *   The field to densify.
    * @param range
    *   The range.
    * @param options
    *   The densify options.
    * @return
    *   Aggregate with requested pipeline stage [[https://docs.mongodb.com/manual/reference/operator/aggregation/densify/]]
    */
  def densify(field: String, range: DensifyRange, options: DensifyOptions = DensifyOptions.densifyOptions()): Aggregate =
    withAggregate(Aggregates.densify(field, range, options))

  /** Creates a \$fill pipeline stage, which assigns values to fields when they are Null or missing.
    *
    * @param options
    *   The fill options.
    * @param outputs
    *   The FillOutputField.
    * @return
    *   Aggregate with requested pipeline stage [[https://docs.mongodb.com/manual/reference/operator/aggregation/fill/]]
    */
  def fill(options: FillOptions, outputs: List[FillOutputField]): Aggregate =
    withAggregate(Aggregates.fill(options, asJava(outputs)))

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
    * @return
    *   Aggregate with requested pipeline stage [[https://docs.mongodb.com/manual/reference/operator/aggregation/geoNear/]]
    */
  def geoNear(point: Point, distanceField: String, options: GeoNearOptions = GeoNearOptions.geoNearOptions()): Aggregate =
    withAggregate(Aggregates.geoNear(point, distanceField, options))

  /** Creates a \$search pipeline stage supported by MongoDB Atlas. You may use \$meta: "searchScore", e.g., via
    * Projection.metaSearchScore(String), to extract the relevance score assigned to each found document.
    * @param operator
    *   A search operator.
    * @param options
    *   Optional \$search pipeline stage fields.
    * @return
    *   Aggregate with requested pipeline stage [[https://docs.mongodb.com/manual/reference/operator/aggregation/search/]]
    */
  def search(operator: SearchOperator, options: SearchOptions): Aggregate =
    withAggregate(Aggregates.search(operator, options))

  def search(operator: SearchOperator): Aggregate =
    search(operator, SearchOptions.searchOptions())

  /** Creates a \$search pipeline stage supported by MongoDB Atlas. You may use \$meta: "searchScore", e.g., via
    * Projection.metaSearchScore(String), to extract the relevance score assigned to each found document.
    * @param collector
    *   A search collector.
    * @param options
    *   Optional \$search pipeline stage fields.
    * @return
    *   Aggregate with \$search pipeline stage [[https://docs.mongodb.com/manual/reference/operator/aggregation/search/]]
    */
  def search(collector: SearchCollector, options: SearchOptions): Aggregate =
    withAggregate(Aggregates.search(collector, options))

  def search(collector: SearchCollector): Aggregate =
    search(collector, SearchOptions.searchOptions())

  /** Creates a \$searchMeta pipeline stage supported by MongoDB Atlas. Unlike \$search, it does not return found documents, instead it
    * returns metadata, which in case of using the \$search stage may be extracted by using \$\$SEARCH_META variable, e.g., via
    * Projection.computedSearchMeta(String).
    *
    * @param operator
    *   A search operator.
    * @param options
    *   Optional \$search pipeline stage fields.
    * @return
    *   Aggregate with \$searchMeta pipeline stage [[https://docs.mongodb.com/manual/reference/operator/aggregation/searchMeta/]]
    */
  def searchMeta(operator: SearchOperator, options: SearchOptions): Aggregate =
    withAggregate(Aggregates.searchMeta(operator, options))

  def searchMeta(operator: SearchOperator): Aggregate =
    searchMeta(operator, SearchOptions.searchOptions())

  /** Creates a \$searchMeta pipeline stage supported by MongoDB Atlas. Unlike \$search, it does not return found documents, instead it
    * returns metadata, which in case of using the \$search stage may be extracted by using \$\$SEARCH_META variable, e.g., via
    * Projection.computedSearchMeta(String).
    *
    * @param collector
    *   A search collector.
    * @param options
    *   Optional \$search pipeline stage fields.
    * @return
    *   Aggregate with \$searchMeta pipeline stage [[https://docs.mongodb.com/manual/reference/operator/aggregation/searchMeta/]]
    */
  def searchMeta(collector: SearchCollector, options: SearchOptions): Aggregate =
    withAggregate(Aggregates.searchMeta(collector, options))

  def searchMeta(collector: SearchCollector): Aggregate =
    searchMeta(collector, SearchOptions.searchOptions())

  /** Creates a \$setWindowFields pipeline stage, which allows using window operators. This stage partitions the input documents similarly
    * to the \$group pipeline stage, sorts them, computes fields in the documents by computing window functions over windows specified per
    * function, and outputs the documents. The important difference from the \$group pipeline stage is that documents belonging to the same
    * partition or window are not folded into a single document.
    *
    * @param partitionBy
    *   Partitioning of data specified like id in \$group.
    * @param sortBy
    *   Fields to sort by. Sorting is required by certain functions and may be required by some windows. Sorting is used only for the
    *   purpose of computing window functions and does not guarantee ordering of the output documents.
    * @param output
    *   A list of windowed computations. Specifying an empty list is not an error, but the resulting stage does not do anything useful.
    * @return
    *   Aggregate with \$setWindowFields pipeline stage [[https://docs.mongodb.com/manual/reference/operator/aggregation/setWindowFields/]]
    */
  def setWindowFields[TExpression](
      partitionBy: TExpression,
      sortBy: Sort,
      outputs: Seq[WindowOutputField]
  ): Aggregate =
    withAggregate(Aggregates.setWindowFields(partitionBy, sortBy.toBson, asJava(outputs)))

  /** Creates a \$setWindowFields pipeline stage, which allows using window operators. This stage sorts input documents, computes fields in
    * the documents by computing window functions over windows specified per function, and outputs the documents. Here, documents will
    * belong to the same partition.
    *
    * @param sortBy
    *   Fields to sort by. Sorting is required by certain functions and may be required by some windows. Sorting is used only for the
    *   purpose of computing window functions and does not guarantee ordering of the output documents.
    * @param output
    *   A list of windowed computations. Specifying an empty list is not an error, but the resulting stage does not do anything useful.
    * @return
    *   Aggregate with \$setWindowFields pipeline stage [[https://docs.mongodb.com/manual/reference/operator/aggregation/setWindowFields/]]
    */
  def setWindowFields(
      sortBy: Sort,
      outputs: Seq[WindowOutputField]
  ): Aggregate =
    withAggregate(Aggregates.setWindowFields(null, sortBy.toBson, asJava(outputs)))

  /** Merges 2 aggregation pipelines together.
    *
    * @param anotherAggregate
    *   the aggregate to be merged with
    * @return
    *   the aggregate pipeline
    */
  def combinedWith(anotherAggregate: Aggregate): Aggregate =
    new Aggregate(anotherAggregate.aggregates ::: aggregates)

  private[mongo4cats] def toBson: java.util.List[Bson] = asJava(aggregates.reverse)

  override def toString: String = aggregates.reverse.mkString("[", ",", "]")
  override def hashCode(): Int  = aggregates.hashCode()
  override def equals(other: Any): Boolean =
    Option(other) match {
      case Some(agg: Aggregate) => agg.aggregates == aggregates
      case _                    => false
    }
}

object Aggregate extends Aggregate {
  final case class Facet(name: String, pipeline: Aggregate) {
    private[operations] def toJava: JFacet = new JFacet(name, pipeline.toBson)
  }

  private[mongo4cats] val empty: Aggregate = new Aggregate(Nil)
}
