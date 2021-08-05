package mongo4cats.database.queries

import cats.effect.Async
import com.mongodb.ExplainVerbosity
import com.mongodb.client.model
import com.mongodb.reactivestreams.client.FindPublisher
import mongo4cats.bson.Document
import mongo4cats.database.helpers._
import mongo4cats.database.operations
import mongo4cats.database.operations.{Index, Projection, Sort}
import org.bson.conversions.Bson

import scala.concurrent.duration.Duration
import scala.reflect.ClassTag

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

  def explain[F[_]: Async]: F[Document] =
    applyCommands().explain().asyncSingle[F]

  def explain[F[_]: Async](verbosity: ExplainVerbosity): F[Document] =
    applyCommands().explain(verbosity).asyncSingle[F]
}
