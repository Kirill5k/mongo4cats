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
import cats.syntax.functor._
import com.mongodb.ExplainVerbosity
import com.mongodb.client.model
import com.mongodb.reactivestreams.client.AggregatePublisher
import mongo4cats.bson.Document
import mongo4cats.helpers._
import mongo4cats.database.operations.Index
import org.bson.conversions.Bson

import scala.concurrent.duration.Duration
import scala.reflect.ClassTag

final case class AggregateQueryBuilder[T: ClassTag] private[database] (
    protected val observable: AggregatePublisher[T],
    protected val commands: List[AggregateCommand[T]]
) extends QueryBuilder[AggregatePublisher, T] {

  /** Enables writing to temporary files. A null value indicates that it's unspecified.
    *
    * @param allowDiskUse
    *   true if writing to temporary files is enabled
    * @return
    *   AggregateQueryBuilder
    */
  def allowDiskUse(allowDiskUse: Boolean): AggregateQueryBuilder[T] =
    AggregateQueryBuilder(observable, AggregateCommand.AllowDiskUse[T](allowDiskUse) :: commands)

  /** Sets the maximum execution time on the server for this operation.
    *
    * @param duration
    *   the max time
    * @return
    *   AggregateQueryBuilder
    */
  def maxTime(duration: Duration): AggregateQueryBuilder[T] =
    AggregateQueryBuilder(observable, AggregateCommand.MaxTime[T](duration) :: commands)

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
  def maxAwaitTime(duration: Duration): AggregateQueryBuilder[T] =
    AggregateQueryBuilder(observable, AggregateCommand.MaxAwaitTime[T](duration) :: commands)

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
  def bypassDocumentValidation(bypassDocumentValidation: Boolean): AggregateQueryBuilder[T] =
    AggregateQueryBuilder(observable, AggregateCommand.BypassDocumentValidation[T](bypassDocumentValidation) :: commands)

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
  def collation(collation: model.Collation): AggregateQueryBuilder[T] =
    AggregateQueryBuilder(observable, AggregateCommand.Collation[T](collation) :: commands)

  /** Sets the comment to the aggregation.
    *
    * @param comment
    *   the comment
    * @return
    *   AggregateQueryBuilder
    * @since 1.7
    */
  def comment(comment: String): AggregateQueryBuilder[T] =
    AggregateQueryBuilder(observable, AggregateCommand.Comment[T](comment) :: commands)

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
  def let(variables: Bson): AggregateQueryBuilder[T] =
    AggregateQueryBuilder(observable, AggregateCommand.Let[T](variables) :: commands)

  /** Sets the hint for which index to use.
    *
    * @param hint
    *   the hint
    * @return
    *   AggregateQueryBuilder
    * @since 1.7
    */
  def hint(hint: Bson): AggregateQueryBuilder[T] =
    AggregateQueryBuilder(observable, AggregateCommand.Hint[T](hint) :: commands)

  def hint(index: Index): AggregateQueryBuilder[T] =
    hint(index.toBson)

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
  def batchSize(batchSize: Int): AggregateQueryBuilder[T] =
    AggregateQueryBuilder(observable, AggregateCommand.BatchSize[T](batchSize) :: commands)

  /** Aggregates documents according to the specified aggregation pipeline, which must end with a \$out stage.
    *
    * @return
    *   a unit, that indicates when the operation has completed
    */
  def toCollection[F[_]: Async]: F[Unit] =
    applyCommands().toCollection.asyncVoid[F]

  def first[F[_]: Async]: F[Option[T]] =
    applyCommands().first().asyncSingle[F].map(Option.apply)

  def all[F[_]: Async]: F[Iterable[T]] =
    applyCommands().asyncIterable[F]

  def stream[F[_]: Async]: fs2.Stream[F, T] =
    applyCommands().stream[F]

  def boundedStream[F[_]: Async](capacity: Int): fs2.Stream[F, T] =
    applyCommands().boundedStream[F](capacity)

  /** Explain the execution plan for this operation with the server's default verbosity level
    *
    * @return
    *   the execution plan
    * @since 4.2
    */
  def explain[F[_]: Async]: F[Document] =
    applyCommands().explain().asyncSingle[F]

  /** Explain the execution plan for this operation with the given verbosity level
    *
    * @param verbosity
    *   the verbosity of the explanation
    * @return
    *   the execution plan
    * @since 4.2
    */
  def explain[F[_]: Async](verbosity: ExplainVerbosity): F[Document] =
    applyCommands().explain(verbosity).asyncSingle[F]
}
