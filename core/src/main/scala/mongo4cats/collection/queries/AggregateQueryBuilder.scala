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

import cats.effect.Async
import cats.syntax.functor._
import com.mongodb.ExplainVerbosity
import com.mongodb.client.model
import com.mongodb.reactivestreams.client.AggregatePublisher
import mongo4cats.bson.Document
import mongo4cats.helpers._
import mongo4cats.collection.operations.Index
import org.bson.conversions.Bson

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration
import scala.reflect.ClassTag

private[mongo4cats] trait AggregateQueries[T, QB] extends QueryBuilder[AggregatePublisher, T, QB] {

  /** Enables writing to temporary files. A null value indicates that it's unspecified.
    *
    * @param allowDiskUse
    *   true if writing to temporary files is enabled
    * @return
    *   AggregateQueryBuilder
    */
  def allowDiskUse(allowDiskUse: Boolean): QB = withQuery(QueryCommand.AllowDiskUse(allowDiskUse))

  /** Sets the maximum execution time on the server for this operation.
    *
    * @param duration
    *   the max time
    * @return
    *   AggregateQueryBuilder
    */
  def maxTime(duration: Duration): QB = withQuery(QueryCommand.MaxTime(duration))

  /** The maximum amount of time for the server to wait on new documents to satisfy a \$changeStream aggregation.
    *
    * A zero value will be ignored.
    *
    * @param duration
    *   the max await time
    * @return
    *   the maximum await execution time in the given time unit
    * @since 1.6
    */
  def maxAwaitTime(duration: Duration): QB = withQuery(QueryCommand.MaxAwaitTime(duration))

  /** Sets the bypass document level validation flag.
    *
    * <p>Note: This only applies when an \$out stage is specified</p>.
    *
    * @param bypassDocumentValidation
    *   If true, allows the write to opt-out of document level validation.
    * @return
    *   AggregateQueryBuilder
    * @since 1.2
    */
  def bypassDocumentValidation(bypassDocumentValidation: Boolean): QB =
    withQuery(QueryCommand.BypassDocumentValidation(bypassDocumentValidation))

  /** Sets the collation options
    *
    * <p>A null value represents the server default.</p>
    *
    * @param collation
    *   the collation options to use
    * @return
    *   AggregateQueryBuilder
    * @since 1.3
    */
  def collation(collation: model.Collation): QB = withQuery(QueryCommand.Collation(collation))

  /** Sets the comment to the aggregation.
    *
    * @param comment
    *   the comment
    * @return
    *   AggregateQueryBuilder
    * @since 1.7
    */
  def comment(comment: String): QB = withQuery(QueryCommand.Comment(comment))

  /** Add top-level variables to the aggregation. <p> For MongoDB 5.0+, the aggregate command accepts a {@code let} option. This option is a
    * document consisting of zero or more fields representing variables that are accessible to the aggregation pipeline. The key is the name
    * of the variable and the value is a constant in the aggregate expression language. Each parameter name is then usable to access the
    * value of the corresponding expression with the "\$\$" syntax within aggregate expression contexts which may require the use of \$expr
    * or a pipeline. </p>
    *
    * @param variables
    *   the variables
    * @return
    *   AggregateQueryBuilder
    * @since 4.3
    */
  def let(variables: Bson): QB = withQuery(QueryCommand.Let(variables))

  /** Sets the hint for which index to use.
    *
    * @param hint
    *   the hint
    * @return
    *   AggregateQueryBuilder
    * @since 1.7
    */
  def hint(hint: Bson): QB = withQuery(QueryCommand.Hint(hint))
  def hint(index: Index): QB = hint(index.toBson)

  /** Sets the number of documents to return per batch.
    *
    * <p>Overrides the Subscription#request value for setting the batch size, allowing for fine grained control over the underlying
    * cursor.</p>
    *
    * @param batchSize
    *   the batch size
    * @return
    *   AggregateQueryBuilder
    * @since 1.8
    */
  def batchSize(batchSize: Int): QB = withQuery(QueryCommand.BatchSize(batchSize))

  override protected def applyQueries(): AggregatePublisher[T] =
    queries.reverse.foldLeft(observable) { case (obs, command) =>
      command match {
        case QueryCommand.Comment(comment)                                   => obs.comment(comment)
        case QueryCommand.Collation(collation)                               => obs.collation(collation)
        case QueryCommand.MaxTime(duration)                                  => obs.maxTime(duration.toNanos, TimeUnit.NANOSECONDS)
        case QueryCommand.MaxAwaitTime(duration)                             => obs.maxAwaitTime(duration.toNanos, TimeUnit.NANOSECONDS)
        case QueryCommand.HintString(hint)                                   => obs.hintString(hint)
        case QueryCommand.Hint(hint)                                         => obs.hint(hint)
        case QueryCommand.BatchSize(size)                                    => obs.batchSize(size)
        case QueryCommand.AllowDiskUse(allowDiskUse)                         => obs.allowDiskUse(allowDiskUse)
        case QueryCommand.BypassDocumentValidation(bypassDocumentValidation) => obs.bypassDocumentValidation(bypassDocumentValidation)
        case QueryCommand.Let(variables)                                     => obs.let(variables)
        case _                                                               => obs
      }
    }
}

final case class AggregateQueryBuilder[F[_]: Async, T: ClassTag] private[collection] (
    protected val observable: AggregatePublisher[T],
    protected val queries: List[QueryCommand]
) extends AggregateQueries[T, AggregateQueryBuilder[F, T]] {

  /** Aggregates documents according to the specified aggregation pipeline, which must end with a \$out stage.
    *
    * @return
    *   a unit, that indicates when the operation has completed
    */
  def toCollection: F[Unit] = applyQueries().toCollection.asyncVoid[F]

  def first: F[Option[T]]                            = applyQueries().first().asyncSingle[F].map(Option.apply)
  def all: F[Iterable[T]]                            = applyQueries().asyncIterable[F]
  def stream: fs2.Stream[F, T]                       = applyQueries().stream[F]
  def boundedStream(capacity: Int): fs2.Stream[F, T] = applyQueries().boundedStream[F](capacity)

  /** Explain the execution plan for this operation with the server's default verbosity level
    *
    * @return
    *   the execution plan
    * @since 4.2
    */
  def explain: F[Document] = applyQueries().explain().asyncSingle[F].map(Document.fromJava)

  /** Explain the execution plan for this operation with the given verbosity level
    *
    * @param verbosity
    *   the verbosity of the explanation
    * @return
    *   the execution plan
    * @since 4.2
    */
  def explain(verbosity: ExplainVerbosity): F[Document] = applyQueries().explain(verbosity).asyncSingle[F].map(Document.fromJava)

  override protected def withQuery(command: QueryCommand): AggregateQueryBuilder[F, T] =
    AggregateQueryBuilder(observable, command :: queries)
}
