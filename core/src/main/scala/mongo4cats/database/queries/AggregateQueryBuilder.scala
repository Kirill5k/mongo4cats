package mongo4cats.database.queries

import cats.effect.Async
import com.mongodb.ExplainVerbosity
import com.mongodb.client.model
import com.mongodb.reactivestreams.client.AggregatePublisher
import mongo4cats.bson.Document
import mongo4cats.database.helpers._
import mongo4cats.database.operations.Index
import org.bson.conversions.Bson

import scala.concurrent.duration.Duration
import scala.reflect.ClassTag

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

  def explain[F[_]: Async]: F[Document] =
    applyCommands().explain().asyncSingle[F]

  def explain[F[_]: Async](verbosity: ExplainVerbosity): F[Document] =
    applyCommands().explain(verbosity).asyncSingle[F]
}
