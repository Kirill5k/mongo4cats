package mongo4cats.database.queries

import cats.effect.Async
import com.mongodb.client.model
import com.mongodb.client.model.changestream.{ChangeStreamDocument, FullDocument}
import com.mongodb.reactivestreams.client.ChangeStreamPublisher
import mongo4cats.database.helpers._
import org.bson.{BsonDocument, BsonTimestamp}

import scala.concurrent.duration.Duration
import scala.reflect.ClassTag

final case class WatchQueryBuilder[T: ClassTag] private[database] (
    protected val observable: ChangeStreamPublisher[T],
    protected val commands: List[WatchCommand[T]]
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
  def batchSize(size: Int): WatchQueryBuilder[T] =
    WatchQueryBuilder(observable, WatchCommand.BatchSize[T](size) :: commands)

  /** Sets the collation options
    *
    * @param collation
    *   the collation options to use
    * @return
    *   WatchQueryBuilder
    */
  def collation(collation: model.Collation): WatchQueryBuilder[T] =
    WatchQueryBuilder(observable, WatchCommand.Collation[T](collation) :: commands)

  /** Sets the fullDocument value.
    *
    * @param fullDocument
    *   the fullDocument
    * @return
    *   WatchQueryBuilder
    */
  def fullDocument(fullDocument: FullDocument): WatchQueryBuilder[T] =
    WatchQueryBuilder(observable, WatchCommand.FullDocument[T](fullDocument) :: commands)

  /** Sets the maximum await execution time on the server for this operation.
    *
    * @param maxAwaitTime
    *   the max await time.
    * @return
    *   WatchQueryBuilder
    */
  def maxAwaitTime(maxAwaitTime: Duration): WatchQueryBuilder[T] =
    WatchQueryBuilder(observable, WatchCommand.MaxAwaitTime[T](maxAwaitTime) :: commands)

  /** Sets the logical starting point for the new change stream.
    *
    * @param resumeToken
    *   the resume token
    * @return
    *   WatchQueryBuilder
    */
  def resumeAfter(resumeToken: BsonDocument): WatchQueryBuilder[T] =
    WatchQueryBuilder(observable, WatchCommand.ResumeAfter[T](resumeToken) :: commands)

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
  def startAfter(startAfter: BsonDocument): WatchQueryBuilder[T] =
    WatchQueryBuilder(observable, WatchCommand.StartAfter[T](startAfter) :: commands)

  /** The change stream will only provide changes that occurred after the specified timestamp.
    *
    * <p>Any command run against the server will return an operation time that can be used here.</p> <p>The default value is an operation
    * time obtained from the server before the change stream was created.</p>
    *
    * @param startAtOperationTime
    *   the start at operation time
    * @since 1.9
    *   @return WatchQueryBuilder
    */
  def startAtOperationTime(startAtOperationTime: BsonTimestamp): WatchQueryBuilder[T] =
    WatchQueryBuilder(observable, WatchCommand.StartAtOperationTime[T](startAtOperationTime) :: commands)

  def first[F[_]: Async]: F[ChangeStreamDocument[T]] =
    applyCommands().first().asyncSingle[F]

  def stream[F[_]: Async]: fs2.Stream[F, ChangeStreamDocument[T]] =
    applyCommands().stream[F]

  def boundedStream[F[_]: Async](capacity: Int): fs2.Stream[F, ChangeStreamDocument[T]] =
    applyCommands().boundedStream[F](capacity)
}
