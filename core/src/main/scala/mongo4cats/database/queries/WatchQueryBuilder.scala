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
