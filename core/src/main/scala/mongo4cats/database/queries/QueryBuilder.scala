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
import com.mongodb.client.model.changestream.{ChangeStreamDocument, FullDocument}
import com.mongodb.reactivestreams.client.{AggregatePublisher, ChangeStreamPublisher, DistinctPublisher, FindPublisher}
import mongo4cats.database.helpers._
import mongo4cats.database.operations
import mongo4cats.database.operations.{Index, Projection, Sort}
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

  def maxTime(duration: Duration): FindQueryBuilder[T] =
    FindQueryBuilder[T](observable, FindCommand.MaxTime[T](duration) :: commands)

  def collation(collation: model.Collation): FindQueryBuilder[T] =
    FindQueryBuilder[T](observable, FindCommand.Collation[T](collation) :: commands)

  def partial(partial: Boolean): FindQueryBuilder[T] =
    FindQueryBuilder[T](observable, FindCommand.Partial[T](partial) :: commands)

  def comment(comment: String): FindQueryBuilder[T] =
    FindQueryBuilder[T](observable, FindCommand.Comment[T](comment) :: commands)

  def returnKey(returnKey: Boolean): FindQueryBuilder[T] =
    FindQueryBuilder[T](observable, FindCommand.ReturnKey[T](returnKey) :: commands)

  def showRecordId(showRecordId: Boolean): FindQueryBuilder[T] =
    FindQueryBuilder[T](observable, FindCommand.ShowRecordId[T](showRecordId) :: commands)

  def hint(index: Bson): FindQueryBuilder[T] =
    FindQueryBuilder[T](observable, FindCommand.Hint[T](index) :: commands)

  def hint(index: Index): FindQueryBuilder[T] =
    hint(index.toBson)

  def max(index: Bson): FindQueryBuilder[T] =
    FindQueryBuilder[T](observable, FindCommand.Max[T](index) :: commands)

  def max(index: Index): FindQueryBuilder[T] =
    max(index.toBson)

  def min(index: Bson): FindQueryBuilder[T] =
    FindQueryBuilder[T](observable, FindCommand.Min[T](index) :: commands)

  def min(index: Index): FindQueryBuilder[T] =
    min(index.toBson)

  def sort(sort: Bson): FindQueryBuilder[T] =
    FindQueryBuilder[T](observable, FindCommand.Sort[T](sort) :: commands)

  def sort(sorts: Sort): FindQueryBuilder[T] =
    sort(sorts.toBson)

  def sortBy(fieldNames: String*): FindQueryBuilder[T] =
    sort(Sort.asc(fieldNames: _*))

  def sortByDesc(fieldNames: String*): FindQueryBuilder[T] =
    sort(Sort.desc(fieldNames: _*))

  def filter(filter: Bson): FindQueryBuilder[T] =
    FindQueryBuilder[T](observable, FindCommand.Filter[T](filter) :: commands)

  def filter(filters: operations.Filter): FindQueryBuilder[T] =
    filter(filters.toBson)

  def projection(projection: Bson): FindQueryBuilder[T] =
    FindQueryBuilder[T](observable, FindCommand.Projection[T](projection) :: commands)

  def projection(projection: Projection): FindQueryBuilder[T] =
    FindQueryBuilder[T](observable, FindCommand.Projection[T](projection.toBson) :: commands)

  def skip(skip: Int): FindQueryBuilder[T] =
    FindQueryBuilder[T](observable, FindCommand.Skip[T](skip) :: commands)

  def limit(limit: Int): FindQueryBuilder[T] =
    FindQueryBuilder[T](observable, FindCommand.Limit[T](limit) :: commands)

  def first[F[_]: Async]: F[T] =
    applyCommands().first().asyncSingle[F]

  def all[F[_]: Async]: F[Iterable[T]] =
    applyCommands().asyncIterable[F]

  def stream[F[_]: Async]: fs2.Stream[F, T] =
    applyCommands().stream[F]

  def boundedStream[F[_]: Async](capacity: Int): fs2.Stream[F, T] =
    applyCommands().boundedStream[F](capacity)
}

final case class DistinctQueryBuilder[T: ClassTag] private[database] (
    protected val observable: DistinctPublisher[T],
    protected val commands: List[DistinctCommand[T]]
) extends QueryBuilder[DistinctPublisher, T] {

  def maxTime(duration: Duration): DistinctQueryBuilder[T] =
    DistinctQueryBuilder[T](observable, DistinctCommand.MaxTime[T](duration) :: commands)

  def filter(filter: Bson): DistinctQueryBuilder[T] =
    DistinctQueryBuilder[T](observable, DistinctCommand.Filter[T](filter) :: commands)

  def filter(filters: operations.Filter): DistinctQueryBuilder[T] =
    filter(filters.toBson)

  def batchSize(size: Int): DistinctQueryBuilder[T] =
    DistinctQueryBuilder[T](observable, DistinctCommand.BatchSize[T](size) :: commands)

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

final case class WatchQueryBuilder[T: ClassTag] private[database] (
    protected val observable: ChangeStreamPublisher[T],
    protected val commands: List[WatchCommand[T]]
) extends QueryBuilder[ChangeStreamPublisher, T] {

  def batchSize(size: Int): WatchQueryBuilder[T] =
    WatchQueryBuilder(observable, WatchCommand.BatchSize[T](size) :: commands)

  def collation(collation: model.Collation): WatchQueryBuilder[T] =
    WatchQueryBuilder(observable, WatchCommand.Collation[T](collation) :: commands)

  def fullDocument(fullDocument: FullDocument): WatchQueryBuilder[T] =
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

  def stream[F[_]: Async]: fs2.Stream[F, ChangeStreamDocument[T]] =
    applyCommands().stream[F]

  def boundedStream[F[_]: Async](capacity: Int): fs2.Stream[F, ChangeStreamDocument[T]] =
    applyCommands().boundedStream[F](capacity)
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

  def let(variables: Bson): AggregateQueryBuilder[T] =
    AggregateQueryBuilder(observable, AggregateCommand.Let[T](variables) :: commands)

  def hint(hint: Bson): AggregateQueryBuilder[T] =
    AggregateQueryBuilder(observable, AggregateCommand.Hint[T](hint) :: commands)

  def hint(index: Index): AggregateQueryBuilder[T] =
    hint(index.toBson)

  def batchSize(batchSize: Int): AggregateQueryBuilder[T] =
    AggregateQueryBuilder(observable, AggregateCommand.BatchSize[T](batchSize) :: commands)

  def toCollection[F[_]: Async]: F[Unit] =
    applyCommands().toCollection.asyncVoid[F]

  def first[F[_]: Async]: F[T] =
    applyCommands().first().asyncSingle[F]

  def all[F[_]: Async]: F[Iterable[T]] =
    applyCommands().asyncIterable[F]

  def stream[F[_]: Async]: fs2.Stream[F, T] =
    applyCommands().stream[F]

  def boundedStream[F[_]: Async](capacity: Int): fs2.Stream[F, T] =
    applyCommands().boundedStream[F](capacity)
}
