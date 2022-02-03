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
import com.mongodb.client.model
import com.mongodb.client.model.changestream.{ChangeStreamDocument, FullDocument}
import com.mongodb.reactivestreams.client.ChangeStreamPublisher
import mongo4cats.helpers._
import org.bson.{BsonDocument, BsonTimestamp}

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration
import scala.reflect.ClassTag

final case class WatchQueryBuilder[F[_]: Async, T: ClassTag] private[collection] (
    private val observable: ChangeStreamPublisher[T],
    private val commands: List[QueryCommand]
) extends QueryBuilder[ChangeStreamPublisher, T] {

  /** Sets the number of documents to return per batch.
    *
    * <p>Overrides the Subscription#request value for setting the batch size, allowing for fine grained control over the underlying
    * cursor.</p>
    *
    * @param size
    *   the batch size
    * @return
    *   WatchQueryBuilder
    * @since 1.8
    */
  def batchSize(size: Int): WatchQueryBuilder[F, T] =
    WatchQueryBuilder(observable, QueryCommand.BatchSize(size) :: commands)

  /** Sets the collation options
    *
    * @param collation
    *   the collation options to use
    * @return
    *   WatchQueryBuilder
    */
  def collation(collation: model.Collation): WatchQueryBuilder[F, T] =
    WatchQueryBuilder(observable, QueryCommand.Collation(collation) :: commands)

  /** Sets the fullDocument value.
    *
    * @param fullDocument
    *   the fullDocument
    * @return
    *   WatchQueryBuilder
    */
  def fullDocument(fullDocument: FullDocument): WatchQueryBuilder[F, T] =
    WatchQueryBuilder(observable, QueryCommand.FullDocument(fullDocument) :: commands)

  /** Sets the maximum await execution time on the server for this operation.
    *
    * @param maxAwaitTime
    *   the max await time.
    * @return
    *   WatchQueryBuilder
    */
  def maxAwaitTime(maxAwaitTime: Duration): WatchQueryBuilder[F, T] =
    WatchQueryBuilder(observable, QueryCommand.MaxAwaitTime(maxAwaitTime) :: commands)

  /** Sets the logical starting point for the new change stream.
    *
    * @param resumeToken
    *   the resume token
    * @return
    *   WatchQueryBuilder
    */
  def resumeAfter(resumeToken: BsonDocument): WatchQueryBuilder[F, T] =
    WatchQueryBuilder(observable, QueryCommand.ResumeAfter(resumeToken) :: commands)

  /** Similar to {@code resumeAfter}, this option takes a resume token and starts a new change stream returning the first notification after
    * the token.
    *
    * <p>This will allow users to watch collections that have been dropped and recreated or newly renamed collections without missing any
    * notifications.</p>
    *
    * <p>Note: The server will report an error if both {@code startAfter} and {@code resumeAfter} are specified.</p>
    *
    * @param startAfter
    *   the startAfter resumeToken
    * @return
    *   WatchQueryBuilder
    */
  def startAfter(startAfter: BsonDocument): WatchQueryBuilder[F, T] =
    WatchQueryBuilder(observable, QueryCommand.StartAfter(startAfter) :: commands)

  /** The change stream will only provide changes that occurred after the specified timestamp.
    *
    * <p>Any command run against the server will return an operation time that can be used here.</p> <p>The default value is an operation
    * time obtained from the server before the change stream was created.</p>
    *
    * @param startAtOperationTime
    *   the start at operation time
    * @since 1.9
    * @return
    *   WatchQueryBuilder
    */
  def startAtOperationTime(startAtOperationTime: BsonTimestamp): WatchQueryBuilder[F, T] =
    WatchQueryBuilder(observable, QueryCommand.StartAtOperationTime(startAtOperationTime) :: commands)

  def stream: fs2.Stream[F, ChangeStreamDocument[T]] =
    applyCommands().stream[F]

  def boundedStream(capacity: Int): fs2.Stream[F, ChangeStreamDocument[T]] =
    applyCommands().boundedStream[F](capacity)

  override protected def applyCommands(): ChangeStreamPublisher[T] =
    commands.reverse.foldLeft(observable) { case (obs, command) =>
      command match {
        case QueryCommand.Collation(collation)                => obs.collation(collation)
        case QueryCommand.MaxAwaitTime(duration)              => obs.maxAwaitTime(duration.toNanos, TimeUnit.NANOSECONDS)
        case QueryCommand.BatchSize(size)                     => obs.batchSize(size)
        case QueryCommand.FullDocument(fullDocument)          => obs.fullDocument(fullDocument)
        case QueryCommand.ResumeAfter(after)                  => obs.resumeAfter(after)
        case QueryCommand.StartAfter(after)                   => obs.startAfter(after)
        case QueryCommand.StartAtOperationTime(operationTime) => obs.startAtOperationTime(operationTime)
        case _                                                => obs
      }
    }
}
