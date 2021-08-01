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

import com.mongodb.client.model.{Aggregates, BucketAutoOptions, GraphLookupOptions, MergeOptions, UnwindOptions}
import org.bson.conversions.Bson

import scala.jdk.CollectionConverters._

object Aggregate {

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
  ): Aggregate = AggregateBuilder(Aggregates.bucketAuto(groupBy, buckets, options))

  /** Creates a \$sample pipeline stage with the specified sample size
    *
    * @param size
    *   the sample size
    * @return
    *   the \$sample pipeline stage [[https://docs.mongodb.com/manual/reference/operator/aggregation/sample/]]
    * @since 3.2
    */
  def sample(size: Int): Aggregate = AggregateBuilder(Aggregates.sample(size))

  /** Creates a \$count pipeline stage using the field name "count" to store the result
    *
    * @return
    *   the \$count pipeline stage [[https://docs.mongodb.com/manual/reference/operator/aggregation/count/]]
    * @since 3.4
    */
  def count: Aggregate = AggregateBuilder(Aggregates.count())

  /** Creates a \$count pipeline stage using the named field to store the result
    *
    * @param field
    *   the field in which to store the count
    * @return
    *   the \$count pipeline stage [[https://docs.mongodb.com/manual/reference/operator/aggregation/count/]]
    * @since 3.4
    */
  def count(field: String): Aggregate = AggregateBuilder(Aggregates.count(field))

  /** Creates a \$match pipeline stage for the specified filter
    *
    * @param filter
    *   the filter to match
    * @return
    *   the \$match pipeline stage [[https://docs.mongodb.com/manual/reference/operator/aggregation/match/]]
    */
  def matchBy(filter: Filter): Aggregate = AggregateBuilder(Aggregates.`match`(filter.toBson))

  /** Creates a \$project pipeline stage for the specified projection
    *
    * @param projection
    *   the projection
    * @return
    *   the \$project pipeline stage [[https://docs.mongodb.com/manual/reference/operator/aggregation/project/]]
    */
  def project(projection: Projection): Aggregate = AggregateBuilder(Aggregates.project(projection.toBson))

  /** Creates a \$sort pipeline stage for the specified sort specification
    *
    * @param sort
    *   the sort specification
    * @return
    *   the \$sort pipeline stage [[https://docs.mongodb.com/manual/reference/operator/aggregation/sort/]]
    */
  def sort(sort: Sort): Aggregate = AggregateBuilder(Aggregates.sort(sort.toBson))

  /** Creates a \$sortByCount pipeline stage for the specified filter
    *
    * @param filter
    *   the filter specification
    * @return
    *   the \$sortByCount pipeline stage [[https://docs.mongodb.com/manual/reference/operator/aggregation/sortByCount/]]
    * @since 3.4
    */
  def sortByCount[TExpression](filter: TExpression): Aggregate = AggregateBuilder(Aggregates.sortByCount(filter))

  /** Creates a \$skip pipeline stage
    *
    * @param n
    *   the number of documents to skip
    * @return
    *   the \$skip pipeline stage [[https://docs.mongodb.com/manual/reference/operator/aggregation/skip/]]
    */
  def skip(n: Int): Aggregate = AggregateBuilder(Aggregates.skip(n))

  /** Creates a \$limit pipeline stage for the specified filter
    *
    * @param n
    *   the limit
    * @return
    *   the \$limit pipeline stage [[https://docs.mongodb.com/manual/reference/operator/aggregation/limi/]]
    */
  def limit(n: Int): Aggregate = AggregateBuilder(Aggregates.limit(n))

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
  def lookup(from: String, localField: String, foreignField: String, as: String): Aggregate =
    AggregateBuilder(Aggregates.lookup(from, localField, foreignField, as))

  /** Creates a \$group pipeline stage for the specified filter
    *
    * @param id
    *   the id expression for the group
    * @param fieldAccumulators
    *   zero or more field accumulator pairs
    * @return
    *   the \$group pipeline stage [[https://docs.mongodb.com/manual/reference/operator/aggregation/group/]]
    */
  def group[TExpression](id: Option[TExpression], fieldAccumulators: Seq[Accumulator]): Aggregate =
    AggregateBuilder(Aggregates.group(id.getOrElse(null.asInstanceOf[TExpression]), fieldAccumulators.map(_.toBson).asJava))

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
  def unwind(fieldName: String, unwindOptions: UnwindOptions = new UnwindOptions()): Aggregate =
    AggregateBuilder(Aggregates.unwind(fieldName, unwindOptions))

  /** Creates a \$out pipeline stage that writes into the specified collection
    *
    * @param collectionName
    *   the collection name
    * @return
    *   the \$out pipeline stage [[https://docs.mongodb.com/manual/reference/operator/aggregation/out/]]
    */
  def out(collectionName: String): Aggregate = AggregateBuilder(Aggregates.out(collectionName))

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
  def out(databaseName: String, collectionName: String): Aggregate =
    AggregateBuilder(Aggregates.out(databaseName, collectionName))

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
  def merge(collectionName: String, options: MergeOptions = new MergeOptions()): Aggregate =
    AggregateBuilder(Aggregates.merge(collectionName, options))

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
  def replaceWith[TExpression](value: TExpression): Aggregate = AggregateBuilder(Aggregates.replaceWith(value))

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
  def lookup(from: String, pipeline: Seq[Aggregate], as: String): Aggregate =
    AggregateBuilder(Aggregates.lookup(from, pipeline.map(_.toBson).asJava, as))

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
  ): Aggregate = AggregateBuilder(Aggregates.graphLookup(from, startWith, connectFromField, connectToField, as, options))

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
  def unionWith(collection: String, pipeline: Seq[Aggregate]): Aggregate =
    AggregateBuilder(Aggregates.unionWith(collection, pipeline.map(_.toBson).asJava))
}

trait Aggregate {
  private[database] def toBson: Bson
}

final private case class AggregateBuilder(
    private val aggregate: Bson
) extends Aggregate {
  override private[database] def toBson: Bson = aggregate
}
