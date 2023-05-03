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

package mongo4cats.queries

import com.mongodb.client.model
import com.mongodb.reactivestreams.client.DistinctPublisher
import mongo4cats.operations
import org.bson.conversions.Bson

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration

private[mongo4cats] trait DistinctQueries[T, QB] extends QueryBuilder[DistinctPublisher, T, QB] {

  /** Sets the maximum execution time on the server for this operation.
    *
    * @param duration
    *   the max time
    * @return
    *   DistinctQueryBuilder
    */
  def maxTime(duration: Duration): QB = withQuery(QueryCommand.MaxTime(duration))

  /** Sets the query filter to apply to the query.
    *
    * @param filter
    *   the filter.
    * @return
    *   DistinctQueryBuilder
    */
  def filter(filter: Bson): QB               = withQuery(QueryCommand.Filter(filter))
  def filter(filters: operations.Filter): QB = filter(filters.toBson)

  /** Sets the number of documents to return per batch.
    *
    * <p>Overrides the Subscription#request value for setting the batch size, allowing for fine grained control over the underlying
    * cursor.</p>
    *
    * @param size
    *   the batch size
    * @return
    *   DistinctQueryBuilder
    * @since 1.8
    */
  def batchSize(size: Int): QB = withQuery(QueryCommand.BatchSize(size))

  /** Sets the collation options
    *
    * @param collation
    *   the collation options to use
    * @return
    *   DistinctQueryBuilder
    * @since 1.3
    */
  def collation(collation: model.Collation): QB = withQuery(QueryCommand.Collation(collation))

  override protected def applyQueries(): DistinctPublisher[T] =
    queries.reverse.foldLeft(observable) { case (obs, command) =>
      command match {
        case QueryCommand.Collation(collation) => obs.collation(collation)
        case QueryCommand.MaxTime(duration)    => obs.maxTime(duration.toNanos, TimeUnit.NANOSECONDS)
        case QueryCommand.Filter(filter)       => obs.filter(filter)
        case QueryCommand.BatchSize(size)      => obs.batchSize(size)
        case _                                 => obs
      }
    }
}

abstract private[mongo4cats] class DistinctQueryBuilder[F[_], T, S[_]] extends DistinctQueries[T, DistinctQueryBuilder[F, T, S]] {
  def first: F[Option[T]]
  def all: F[Iterable[T]]
  def stream: S[T]
  def boundedStream(capacity: Int): S[T]
}
