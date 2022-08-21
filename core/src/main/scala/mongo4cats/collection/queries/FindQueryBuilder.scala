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

package mongo4cats.collection.queries

import com.mongodb.ExplainVerbosity
import com.mongodb.client.model
import com.mongodb.reactivestreams.client.FindPublisher
import mongo4cats.bson.Document
import mongo4cats.collection.operations
import mongo4cats.collection.operations.{Projection, Sort}
import org.bson.conversions.Bson

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration

private[mongo4cats] trait FindQueries[T, QB] extends QueryBuilder[FindPublisher, T, QB] {

  /** Sets the maximum execution time on the server for this operation.
    *
    * @param duration
    *   the max time
    * @return
    *   FindQueryBuilder
    */
  def maxTime(duration: Duration): QB = withQuery(QueryCommand.MaxTime(duration))

  /** The maximum amount of time for the server to wait on new documents to satisfy a tailable cursor query. This only applies to a
    * TAILABLE_AWAIT cursor. When the cursor is not a TAILABLE_AWAIT cursor, this option is ignored.
    *
    * On servers &gt;= 3.2, this option will be specified on the getMore command as "maxTimeMS". The default is no value: no "maxTimeMS" is
    * sent to the server with the getMore command.
    *
    * On servers &lt; 3.2, this option is ignored, and indicates that the driver should respect the server's default value
    *
    * A zero value will be ignored.
    *
    * @param duration
    *   the max await time
    * @return
    *   the maximum await execution time in the given time unit
    */
  def maxAwaitTime(duration: Duration): QB = withQuery(QueryCommand.MaxAwaitTime(duration))

  /** Sets the collation options
    *
    * <p>A null value represents the server default.</p>
    *
    * @param collation
    *   the collation options to use
    * @return
    *   FindQueryBuilder
    * @since 1.3
    */
  def collation(collation: model.Collation): QB = withQuery(QueryCommand.Collation(collation))

  /** Get partial results from a sharded cluster if one or more shards are unreachable (instead of throwing an error).
    *
    * @param partial
    *   if partial results for sharded clusters is enabled
    * @return
    *   FindQueryBuilder
    */
  def partial(partial: Boolean): QB = withQuery(QueryCommand.Partial(partial))

  /** Sets the comment to the query. A null value means no comment is set.
    *
    * @param comment
    *   the comment
    * @return
    *   FindQueryBuilder
    * @since 1.6
    */
  def comment(comment: String): QB = withQuery(QueryCommand.Comment(comment))

  /** Sets the returnKey. If true the find operation will return only the index keys in the resulting documents.
    *
    * @param returnKey
    *   the returnKey
    * @return
    *   FindQueryBuilder
    * @since 1.6
    */
  def returnKey(returnKey: Boolean): QB = withQuery(QueryCommand.ReturnKey(returnKey))

  /** Sets the showRecordId. Set to true to add a field \$recordId to the returned documents.
    *
    * @param showRecordId
    *   the showRecordId
    * @return
    *   FindQueryBuilder
    * @since 1.6
    */
  def showRecordId(showRecordId: Boolean): QB = withQuery(QueryCommand.ShowRecordId(showRecordId))

  /** Sets the hint for which index to use. A null value means no hint is set.
    *
    * @param index
    *   the name of the index which should be used for the operation
    * @return
    *   FindQueryBuilder
    * @since 1.13
    */
  def hint(index: String): QB = withQuery(QueryCommand.HintString(index))

  /** Sets the hint for which index to use. A null value means no hint is set.
    *
    * @param hint
    *   the hint
    * @return
    *   FindQueryBuilder
    * @since 1.6
    */
  def hint(hint: Bson): QB = withQuery(QueryCommand.Hint(hint))

  /** Sets the exclusive upper bound for a specific index. A null value means no max is set.
    *
    * @param max
    *   the max
    * @return
    *   this
    * @since 1.6
    */
  def max(max: Bson): QB = withQuery(QueryCommand.Max(max))

  /** Sets the minimum inclusive lower bound for a specific index. A null value means no max is set.
    *
    * @param min
    *   the min
    * @return
    *   this
    * @since 1.6
    */
  def min(min: Bson): QB = withQuery(QueryCommand.Min(min))

  /** Sets the sort criteria to apply to the query.
    *
    * @param sort
    *   the sort criteria, which may be null.
    * @return
    *   FindQueryBuilder
    */
  def sort(sort: Bson): QB                = withQuery(QueryCommand.Sort(sort))
  def sort(sorts: Sort): QB               = sort(sorts.toBson)
  def sortBy(fieldNames: String*): QB     = sort(Sort.asc(fieldNames: _*))
  def sortByDesc(fieldNames: String*): QB = sort(Sort.desc(fieldNames: _*))

  /** Sets a document describing the fields to return for all matching documents.
    *
    * @param projection
    *   the project document, which may be null.
    * @return
    *   FindQueryBuilder
    */
  def projection(projection: Bson): QB       = withQuery(QueryCommand.Projection(projection))
  def projection(projection: Projection): QB = withQuery(QueryCommand.Projection(projection.toBson))

  /** Sets the number of documents to skip.
    *
    * @param skip
    *   the number of documents to skip
    * @return
    *   FindQueryBuilder
    */
  def skip(skip: Int): QB = withQuery(QueryCommand.Skip(skip))

  /** Sets the limit to apply.
    *
    * @param limit
    *   the limit
    * @return
    *   FindQueryBuilder
    */
  def limit(limit: Int): QB = withQuery(QueryCommand.Limit(limit))

  /** Sets the query filter to apply to the query.
    *
    * @param filter
    *   the filter
    * @return
    *   FindQueryBuilder
    */
  def filter(filter: Bson): QB               = withQuery(QueryCommand.Filter(filter))
  def filter(filters: operations.Filter): QB = filter(filters.toBson)

  override protected def applyQueries(): FindPublisher[T] =
    queries.reverse.foldLeft(observable) { case (obs, command) =>
      command match {
        case QueryCommand.ShowRecordId(showRecordId) => obs.showRecordId(showRecordId)
        case QueryCommand.ReturnKey(returnKey)       => obs.returnKey(returnKey)
        case QueryCommand.Comment(comment)           => obs.comment(comment)
        case QueryCommand.Collation(collation)       => obs.collation(collation)
        case QueryCommand.Partial(partial)           => obs.partial(partial)
        case QueryCommand.MaxTime(duration)          => obs.maxTime(duration.toNanos, TimeUnit.NANOSECONDS)
        case QueryCommand.MaxAwaitTime(duration)     => obs.maxAwaitTime(duration.toNanos, TimeUnit.NANOSECONDS)
        case QueryCommand.HintString(hint)           => obs.hintString(hint)
        case QueryCommand.Hint(hint)                 => obs.hint(hint)
        case QueryCommand.Max(index)                 => obs.max(index)
        case QueryCommand.Min(index)                 => obs.min(index)
        case QueryCommand.Skip(n)                    => obs.skip(n)
        case QueryCommand.Limit(n)                   => obs.limit(n)
        case QueryCommand.Sort(order)                => obs.sort(order)
        case QueryCommand.Filter(filter)             => obs.filter(filter)
        case QueryCommand.Projection(projection)     => obs.projection(projection)
        case QueryCommand.BatchSize(size)            => obs.batchSize(size)
        case QueryCommand.AllowDiskUse(allowDiskUse) => obs.allowDiskUse(allowDiskUse)
        case _                                       => obs
      }
    }
}

abstract class FindQueryBuilder[F[_], T] extends FindQueries[T, FindQueryBuilder[F, T]] {
  def first: F[Option[T]]
  def all: F[Iterable[T]]
  def stream: fs2.Stream[F, T]
  def boundedStream(capacity: Int): fs2.Stream[F, T]

  /** Explain the execution plan for this operation with the server's default verbosity level
    *
    * @return
    *   the execution plan
    * @since 4.2
    */
  def explain: F[Document]

  /** Explain the execution plan for this operation with the given verbosity level
    *
    * @param verbosity
    *   the verbosity of the explanation
    * @return
    *   the execution plan
    * @since 4.2
    */
  def explain(verbosity: ExplainVerbosity): F[Document]
}
