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

package mongo4cats.database.queries

import cats.effect.Async
import com.mongodb.client.model
import com.mongodb.reactivestreams.client.DistinctPublisher
import mongo4cats.database.helpers._
import mongo4cats.database.operations
import org.bson.conversions.Bson

import scala.concurrent.duration.Duration
import scala.reflect.ClassTag

final case class DistinctQueryBuilder[T: ClassTag] private[database] (
    protected val observable: DistinctPublisher[T],
    protected val commands: List[DistinctCommand[T]]
) extends QueryBuilder[DistinctPublisher, T] {

  /** Sets the maximum execution time on the server for this operation.
    *
    * @param duration
    *   the max time
    * @return
    *   DistinctQueryBuilder
    */
  def maxTime(duration: Duration): DistinctQueryBuilder[T] =
    DistinctQueryBuilder[T](observable, DistinctCommand.MaxTime[T](duration) :: commands)

  /** Sets the query filter to apply to the query.
    *
    * @param filter
    *   the filter.
    * @return
    *   DistinctQueryBuilder
    */
  def filter(filter: Bson): DistinctQueryBuilder[T] =
    DistinctQueryBuilder[T](observable, DistinctCommand.Filter[T](filter) :: commands)

  def filter(filters: operations.Filter): DistinctQueryBuilder[T] =
    filter(filters.toBson)

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
  def batchSize(size: Int): DistinctQueryBuilder[T] =
    DistinctQueryBuilder[T](observable, DistinctCommand.BatchSize[T](size) :: commands)

  /** Sets the collation options
    *
    * @param collation
    *   the collation options to use
    * @return
    *   DistinctQueryBuilder
    * @since 1.3
    */
  def collation(collation: model.Collation): DistinctQueryBuilder[T] =
    DistinctQueryBuilder[T](observable, DistinctCommand.Collation[T](collation) :: commands)

  def first[F[_]: Async]: F[T] =
    applyCommands().first().asyncSingle[F]

  def all[F[_]: Async]: F[Iterable[T]] =
    applyCommands().asyncIterable[F]

  def stream[F[_]: Async]: fs2.Stream[F, T] =
    applyCommands().stream[F]

  def boundedStream[F[_]: Async](capacity: Int): fs2.Stream[F, T] =
    applyCommands().boundedStream[F](capacity)
}
