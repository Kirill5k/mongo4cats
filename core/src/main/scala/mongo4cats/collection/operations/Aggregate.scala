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

package mongo4cats.collection.operations

import com.mongodb.client.model.{
  Aggregates,
  BucketAutoOptions,
  GraphLookupOptions,
  MergeOptions,
  UnwindOptions
}
import mongo4cats.bson.BsonEncoder
import mongo4cats.bson.syntax._
import org.bson.conversions.Bson

import scala.jdk.CollectionConverters._

final case class Aggregate private (private val aggs: List[Bson]) {
  def bucketAuto[T: BsonEncoder](
      groupBy: T,
      buckets: Int,
      options: BucketAutoOptions
  ): Aggregate =
    add(Aggregates.bucketAuto(groupBy.asBson, buckets, options))

  def sample(size: Int): Aggregate =
    add(Aggregates.sample(size))

  def count: Aggregate =
    add(Aggregates.count())

  def count(field: String): Aggregate =
    add(Aggregates.count(field))

  def matchBy(filter: Filter): Aggregate =
    add(Aggregates.`match`(filter.toBson))

  def project(projection: Projection): Aggregate =
    add(Aggregates.project(projection.toBson))

  def sort(sort: Sort): Aggregate =
    add(Aggregates.sort(sort.toBson))

  def sortByCount[T: BsonEncoder](filter: T): Aggregate =
    add(Aggregates.sortByCount(filter.asBson))

  def skip(n: Int): Aggregate =
    add(Aggregates.skip(n))

  def limit(n: Int): Aggregate =
    add(Aggregates.limit(n))

  def lookup(from: String, localField: String, foreignField: String, as: String): Aggregate =
    add(Aggregates.lookup(from, localField, foreignField, as))

  def group[T: BsonEncoder](id: T, fieldAccumulator: Accumulator): Aggregate =
    add(Aggregates.group(id.asBson, fieldAccumulator.toBsonFields))

  def unwind(fieldName: String, unwindOptions: UnwindOptions = new UnwindOptions()): Aggregate =
    add(Aggregates.unwind(fieldName, unwindOptions))

  def out(collectionName: String): Aggregate =
    add(Aggregates.out(collectionName))

  def out(databaseName: String, collectionName: String): Aggregate =
    add(Aggregates.out(databaseName, collectionName))

  def merge(collectionName: String, options: MergeOptions = new MergeOptions()): Aggregate =
    add(Aggregates.merge(collectionName, options))

  def replaceWith[T: BsonEncoder](value: T): Aggregate =
    add(Aggregates.replaceWith(value.asBson))

  def lookup(from: String, pipeline: Aggregate, as: String): Aggregate =
    add(Aggregates.lookup(from, pipeline.toBsons.asJava, as))

  def graphLookup[T: BsonEncoder](
      from: String,
      startWith: T,
      connectFromField: String,
      connectToField: String,
      as: String,
      options: GraphLookupOptions = new GraphLookupOptions()
  ): Aggregate =
    add(
      Aggregates.graphLookup(
        from,
        startWith.asBson,
        connectFromField,
        connectToField,
        as,
        options
      )
    )

  def unionWith(collection: String, pipeline: Aggregate): Aggregate =
    add(Aggregates.unionWith(collection, pipeline.toBsons.asJava))

  def combineWith(other: Aggregate) =
    copy(aggs = other.aggs ::: aggs)

  def toBsons: List[Bson] =
    aggs.reverse

  private def add(doc: Bson): Aggregate =
    copy(aggs = doc :: aggs)

}

object Aggregate {
  def empty: Aggregate = Aggregate(List.empty)

  def bucketAuto[T: BsonEncoder](
      groupBy: T,
      buckets: Int,
      options: BucketAutoOptions
  ): Aggregate =
    empty.bucketAuto[T](groupBy, buckets, options)

  def sample(size: Int): Aggregate =
    empty.sample(size)

  def count: Aggregate =
    empty.count

  def count(field: String): Aggregate =
    empty.count(field)

  def matchBy(filter: Filter): Aggregate =
    empty.matchBy(filter)

  def project(projection: Projection): Aggregate =
    empty.project(projection)

  def sort(sort: Sort): Aggregate =
    empty.sort(sort)

  def sortByCount[T: BsonEncoder](filter: T): Aggregate =
    empty.sortByCount[T](filter)

  def skip(n: Int): Aggregate =
    empty.skip(n)

  def limit(n: Int): Aggregate =
    empty.limit(n)

  def lookup(from: String, localField: String, foreignField: String, as: String): Aggregate =
    empty.lookup(from, localField, foreignField, as)

  def group[T: BsonEncoder](id: T, fieldAccumulator: Accumulator): Aggregate =
    empty.group[T](id, fieldAccumulator)

  def unwind(fieldName: String, unwindOptions: UnwindOptions = new UnwindOptions()): Aggregate =
    empty.unwind(fieldName, unwindOptions)

  def out(collectionName: String): Aggregate =
    empty.out(collectionName)

  def out(databaseName: String, collectionName: String): Aggregate =
    empty.out(databaseName, collectionName)

  def merge(name: String, options: MergeOptions = new MergeOptions()): Aggregate =
    empty.merge(name, options)

  def replaceWith[T: BsonEncoder](value: T): Aggregate =
    empty.replaceWith[T](value)

  def lookup(from: String, pipeline: Aggregate, as: String): Aggregate =
    empty.lookup(from, pipeline, as)

  def graphLookup[T: BsonEncoder](
      from: String,
      startWith: T,
      connectFromField: String,
      connectToField: String,
      as: String,
      options: GraphLookupOptions = new GraphLookupOptions()
  ): Aggregate =
    empty.graphLookup[T](from, startWith, connectFromField, connectToField, as, options)

  def unionWith(collection: String, pipeline: Aggregate): Aggregate =
    empty.unionWith(collection, pipeline)
}
