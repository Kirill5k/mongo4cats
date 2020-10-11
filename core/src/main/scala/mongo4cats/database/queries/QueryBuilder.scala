package mongo4cats.database.queries

import cats.effect.{Async, ConcurrentEffect}
import com.mongodb.client.model
import com.mongodb.client.model.changestream
import com.mongodb.client.model.changestream.ChangeStreamDocument
import mongo4cats.database.helpers.{multipleItemsAsync, singleItemAsync, unicastPublisher}
import fs2.interop.reactivestreams._
import org.bson.conversions.Bson
import org.mongodb.scala.bson.{BsonTimestamp, Document}
import org.mongodb.scala.{AggregateObservable, ChangeStreamObservable, DistinctObservable, FindObservable, Observable}

import scala.concurrent.duration.Duration
import scala.reflect.ClassTag

private[queries] trait QueryBuilder[O[_] <: Observable[_], T] {
  protected def observable: O[T]
  protected def commands: List[QueryCommand[O, T]]

  protected def applyCommands(): O[T] =
    commands.reverse.foldLeft(observable) {
      case (obs, comm) => comm.run(obs)
    }
}

final case class FindQueryBuilder[T: ClassTag] private (
    protected val observable: FindObservable[T],
    protected val commands: List[FindCommand[T]] = Nil
) extends QueryBuilder[FindObservable, T] {

  def sort(sort: Bson): FindQueryBuilder[T] =
    FindQueryBuilder[T](observable, FindCommand.Sort[T](sort) :: commands)

  def filter(filter: Bson): FindQueryBuilder[T] =
    FindQueryBuilder[T](observable, FindCommand.Filter[T](filter) :: commands)

  def projection(projection: Bson): FindQueryBuilder[T] =
    FindQueryBuilder[T](observable, FindCommand.Projection[T](projection) :: commands)

  def limit(limit: Int): FindQueryBuilder[T] =
    FindQueryBuilder[T](observable, FindCommand.Limit[T](limit) :: commands)

  def first[F[_]: Async]: F[T] =
    Async[F].async(singleItemAsync(applyCommands().first()))

  def all[F[_]: Async]: F[Iterable[T]] =
    Async[F].async(multipleItemsAsync(applyCommands()))

  def stream[F[_]: ConcurrentEffect]: fs2.Stream[F, T] =
    unicastPublisher[T](applyCommands()).toStream[F]
}

final case class DistinctQueryBuilder[T: ClassTag] private (
    protected val observable: DistinctObservable[T],
    protected val commands: List[DistinctCommand[T]] = Nil
) extends QueryBuilder[DistinctObservable, T] {

  def filter(filter: Bson): DistinctQueryBuilder[T] =
    DistinctQueryBuilder[T](observable, DistinctCommand.Filter[T](filter) :: commands)

  def batchSize(size: Int): DistinctQueryBuilder[T] =
    DistinctQueryBuilder[T](observable, DistinctCommand.BatchSize[T](size) :: commands)

  def collation(collation: model.Collation): DistinctQueryBuilder[T] =
    DistinctQueryBuilder[T](observable, DistinctCommand.Collation[T](collation) :: commands)

  def first[F[_]: Async]: F[T] =
    Async[F].async(singleItemAsync(applyCommands().first()))

  def all[F[_]: Async]: F[Iterable[T]] =
    Async[F].async(multipleItemsAsync(applyCommands()))

  def stream[F[_]: ConcurrentEffect]: fs2.Stream[F, T] =
    unicastPublisher[T](applyCommands()).toStream[F]
}

final case class WatchQueryBuilder[T: ClassTag] private (
    protected val observable: ChangeStreamObservable[T],
    protected val commands: List[WatchCommand[T]] = Nil
) extends QueryBuilder[ChangeStreamObservable, T] {

  def batchSize(size: Int): WatchQueryBuilder[T] =
    WatchQueryBuilder(observable, WatchCommand.BatchSize(size) :: commands)

  def collation(collation: model.Collation): WatchQueryBuilder[T] =
    WatchQueryBuilder(observable, WatchCommand.Collation(collation) :: commands)

  def fullDocument(fullDocument: changestream.FullDocument): WatchQueryBuilder[T] =
    WatchQueryBuilder(observable, WatchCommand.FullDocument(fullDocument) :: commands)

  def maxAwaitTime(duration: Duration): WatchQueryBuilder[T] =
    WatchQueryBuilder(observable, WatchCommand.MaxAwaitTime(duration) :: commands)

  def resumeAfter(after: Document): WatchQueryBuilder[T] =
    WatchQueryBuilder(observable, WatchCommand.ResumeAfter(after) :: commands)

  def startAfter(after: Document): WatchQueryBuilder[T] =
    WatchQueryBuilder(observable, WatchCommand.StartAfter(after) :: commands)

  def startAtOperationTime(operationTime: BsonTimestamp): WatchQueryBuilder[T] =
    WatchQueryBuilder(observable, WatchCommand.StartAtOperationTime(operationTime) :: commands)

  def first[F[_]: Async]: F[ChangeStreamDocument[T]] =
    Async[F].async(singleItemAsync(applyCommands().first()))

  def stream[F[_]: ConcurrentEffect]: fs2.Stream[F, ChangeStreamDocument[T]] =
    unicastPublisher[ChangeStreamDocument[T]](applyCommands()).toStream[F]
}

final case class AggregateQueryBuilder[T: ClassTag] private (
    protected val observable: AggregateObservable[T],
    protected val commands: List[AggregateCommand[T]] = Nil
) extends QueryBuilder[AggregateObservable, T] {

  def allowDiskUse(allowDiskUse: Boolean): AggregateQueryBuilder[T] =
    AggregateQueryBuilder(observable, AggregateCommand.AllowDiskUse(allowDiskUse) :: commands)

  def maxTime(duration: Duration): AggregateQueryBuilder[T] =
    AggregateQueryBuilder(observable, AggregateCommand.MaxTime(duration) :: commands)

  def maxAwaitTime(duration: Duration): AggregateQueryBuilder[T] =
    AggregateQueryBuilder(observable, AggregateCommand.MaxAwaitTime(duration) :: commands)

  def bypassDocumentValidation(bypassDocumentValidation: Boolean): AggregateQueryBuilder[T] =
    AggregateQueryBuilder(observable, AggregateCommand.BypassDocumentValidation(bypassDocumentValidation) :: commands)

  def collation(collation: model.Collation): AggregateQueryBuilder[T] =
    AggregateQueryBuilder(observable, AggregateCommand.Collation(collation) :: commands)

  def comment(comment: String): AggregateQueryBuilder[T] =
    AggregateQueryBuilder(observable, AggregateCommand.Comment(comment) :: commands)

  def hint(hint: Bson): AggregateQueryBuilder[T] =
    AggregateQueryBuilder(observable, AggregateCommand.Hint(hint) :: commands)

  def batchSize(batchSize: Int): AggregateQueryBuilder[T] =
    AggregateQueryBuilder(observable, AggregateCommand.BatchSize(batchSize) :: commands)

  def first[F[_]: Async]: F[T] =
    Async[F].async(singleItemAsync(applyCommands().first()))

  def stream[F[_]: ConcurrentEffect]: fs2.Stream[F, T] =
    unicastPublisher[T](applyCommands()).toStream[F]

  def all[F[_]: Async]: F[Iterable[T]] =
    Async[F].async(multipleItemsAsync(applyCommands()))
}
