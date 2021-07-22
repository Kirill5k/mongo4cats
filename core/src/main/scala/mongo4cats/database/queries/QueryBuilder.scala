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
import com.mongodb.client.model.changestream
import com.mongodb.client.model.changestream.ChangeStreamDocument
import com.mongodb.reactivestreams.client.{AggregatePublisher, ChangeStreamPublisher, DistinctPublisher, FindPublisher}
import mongo4cats.database.helpers._
import org.bson.{BsonDocument, BsonTimestamp}
import org.bson.conversions.Bson
import org.reactivestreams.Publisher

import scala.concurrent.duration.Duration
import scala.reflect.ClassTag

private[queries] trait QueryBuilder[O[_] <: Publisher[_], T] {
  protected def observable: O[T]
  protected def commands: List[QueryCommand[O, T]]

  protected def applyCommands(): O[T] =
    commands.reverse.foldLeft(observable) { case (obs, comm) => comm.run(obs) }
}

final case class FindQueryBuilder[T: ClassTag] private[database] (
    protected val observable: FindPublisher[T],
    protected val commands: List[FindCommand[T]]
) extends QueryBuilder[FindPublisher, T] {

  def sort(sort: Bson): FindQueryBuilder[T] =
    FindQueryBuilder[T](observable, FindCommand.Sort[T](sort) :: commands)

  def filter(filter: Bson): FindQueryBuilder[T] =
    FindQueryBuilder[T](observable, FindCommand.Filter[T](filter) :: commands)

  def projection(projection: Bson): FindQueryBuilder[T] =
    FindQueryBuilder[T](observable, FindCommand.Projection[T](projection) :: commands)

  def skip(skip: Int): FindQueryBuilder[T] =
    FindQueryBuilder[T](observable, FindCommand.Skip[T](skip) :: commands)

  def limit(limit: Int): FindQueryBuilder[T] =
    FindQueryBuilder[T](observable, FindCommand.Limit[T](limit) :: commands)

  def first[F[_]: Async]: F[T] =
    applyCommands().first().asyncSingle[F]

  def all[F[_]: Async]: F[Iterable[T]] =
    applyCommands().asyncIterable[F]

  def stream[F[_]: Async](queueCapacity: Int = 50): fs2.Stream[F, T] =
    applyCommands().stream[F](queueCapacity)
}

final case class DistinctQueryBuilder[T: ClassTag] private[database] (
    protected val observable: DistinctPublisher[T],
    protected val commands: List[DistinctCommand[T]]
) extends QueryBuilder[DistinctPublisher, T] {

  def filter(filter: Bson): DistinctQueryBuilder[T] =
    DistinctQueryBuilder[T](observable, DistinctCommand.Filter[T](filter) :: commands)

  def batchSize(size: Int): DistinctQueryBuilder[T] =
    DistinctQueryBuilder[T](observable, DistinctCommand.BatchSize[T](size) :: commands)

  def collation(collation: model.Collation): DistinctQueryBuilder[T] =
    DistinctQueryBuilder[T](observable, DistinctCommand.Collation[T](collation) :: commands)

  def first[F[_]: Async]: F[T] =
    applyCommands().first().asyncSingle[F]

  def all[F[_]: Async]: F[Iterable[T]] =
    applyCommands().asyncIterable[F]

  def stream[F[_]: Async](queueCapacity: Int = 50): fs2.Stream[F, T] =
    applyCommands().stream[F](queueCapacity)
}

final case class WatchQueryBuilder[T: ClassTag] private[database] (
    protected val observable: ChangeStreamPublisher[T],
    protected val commands: List[WatchCommand[T]]
) extends QueryBuilder[ChangeStreamPublisher, T] {

  def batchSize(size: Int): WatchQueryBuilder[T] =
    WatchQueryBuilder(observable, WatchCommand.BatchSize[T](size) :: commands)

  def collation(collation: model.Collation): WatchQueryBuilder[T] =
    WatchQueryBuilder(observable, WatchCommand.Collation[T](collation) :: commands)

  def fullDocument(fullDocument: changestream.FullDocument): WatchQueryBuilder[T] =
    WatchQueryBuilder(observable, WatchCommand.FullDocument[T](fullDocument) :: commands)

  def maxAwaitTime(duration: Duration): WatchQueryBuilder[T] =
    WatchQueryBuilder(observable, WatchCommand.MaxAwaitTime[T](duration) :: commands)

  def resumeAfter(after: BsonDocument): WatchQueryBuilder[T] =
    WatchQueryBuilder(observable, WatchCommand.ResumeAfter[T](after) :: commands)

  def startAfter(after: BsonDocument): WatchQueryBuilder[T] =
    WatchQueryBuilder(observable, WatchCommand.StartAfter[T](after) :: commands)

  def startAtOperationTime(operationTime: BsonTimestamp): WatchQueryBuilder[T] =
    WatchQueryBuilder(observable, WatchCommand.StartAtOperationTime[T](operationTime) :: commands)

  def first[F[_]: Async]: F[ChangeStreamDocument[T]] =
    applyCommands().first().asyncSingle[F]

  def stream[F[_]: Async](queueCapacity: Int = 50): fs2.Stream[F, ChangeStreamDocument[T]] =
    applyCommands().stream[F](queueCapacity)
}

final case class AggregateQueryBuilder[T: ClassTag] private[database] (
    protected val observable: AggregatePublisher[T],
    protected val commands: List[AggregateCommand[T]]
) extends QueryBuilder[AggregatePublisher, T] {

  def allowDiskUse(allowDiskUse: Boolean): AggregateQueryBuilder[T] =
    AggregateQueryBuilder(observable, AggregateCommand.AllowDiskUse[T](allowDiskUse) :: commands)

  def maxTime(duration: Duration): AggregateQueryBuilder[T] =
    AggregateQueryBuilder(observable, AggregateCommand.MaxTime[T](duration) :: commands)

  def maxAwaitTime(duration: Duration): AggregateQueryBuilder[T] =
    AggregateQueryBuilder(observable, AggregateCommand.MaxAwaitTime[T](duration) :: commands)

  def bypassDocumentValidation(bypassDocumentValidation: Boolean): AggregateQueryBuilder[T] =
    AggregateQueryBuilder(observable, AggregateCommand.BypassDocumentValidation[T](bypassDocumentValidation) :: commands)

  def collation(collation: model.Collation): AggregateQueryBuilder[T] =
    AggregateQueryBuilder(observable, AggregateCommand.Collation[T](collation) :: commands)

  def comment(comment: String): AggregateQueryBuilder[T] =
    AggregateQueryBuilder(observable, AggregateCommand.Comment[T](comment) :: commands)

  def hint(hint: Bson): AggregateQueryBuilder[T] =
    AggregateQueryBuilder(observable, AggregateCommand.Hint[T](hint) :: commands)

  def batchSize(batchSize: Int): AggregateQueryBuilder[T] =
    AggregateQueryBuilder(observable, AggregateCommand.BatchSize[T](batchSize) :: commands)

  def first[F[_]: Async]: F[T] =
    applyCommands().first().asyncSingle[F]

  def all[F[_]: Async]: F[Iterable[T]] =
    applyCommands().asyncIterable[F]

  def stream[F[_]: Async](queueCapacity: Int = 50): fs2.Stream[F, T] =
    applyCommands().stream[F](queueCapacity)
}
