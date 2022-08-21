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
import com.mongodb.client.model.changestream.ChangeStreamDocument
import com.mongodb.reactivestreams.client.{AggregatePublisher, ChangeStreamPublisher, DistinctPublisher, FindPublisher}
import mongo4cats.bson.Document
import org.reactivestreams.Publisher
import mongo4cats.helpers._

import scala.reflect.ClassTag

private[mongo4cats] trait QueryBuilder[O[_] <: Publisher[_], T, QB] {
  protected def observable: O[T]
  protected def queries: List[QueryCommand]
  protected def withQuery(command: QueryCommand): QB
  protected def applyQueries(): O[T]
}

private[collection] object QueryBuilder {
  def watch[F[_]: Async, T: ClassTag](observable: ChangeStreamPublisher[T]): WatchQueryBuilder[F, T] =
    Fs2WatchQueryBuilder(observable, Nil)

  def find[F[_]: Async, T: ClassTag](observable: FindPublisher[T]): FindQueryBuilder[F, T] =
    Fs2FindQueryBuilder(observable, Nil)

  def distinct[F[_]: Async, T: ClassTag](observable: DistinctPublisher[T]): DistinctQueryBuilder[F, T] =
    Fs2DistinctQueryBuilder(observable, Nil)

  def aggregate[F[_]: Async, T: ClassTag](observable: AggregatePublisher[T]): AggregateQueryBuilder[F, T] =
    Fs2AggregateQueryBuilder(observable, Nil)

  final private case class Fs2WatchQueryBuilder[F[_]: Async, T: ClassTag](
      protected val observable: ChangeStreamPublisher[T],
      protected val queries: List[QueryCommand]
  ) extends WatchQueryBuilder[F, T] {

    def stream: fs2.Stream[F, ChangeStreamDocument[T]]                               = applyQueries().stream[F]
    def boundedStream(capacity: Int): fs2.Stream[F, ChangeStreamDocument[T]]         = applyQueries().boundedStream[F](capacity)
    override protected def withQuery(command: QueryCommand): WatchQueryBuilder[F, T] = Fs2WatchQueryBuilder(observable, command :: queries)
  }

  final case class Fs2FindQueryBuilder[F[_]: Async, T: ClassTag](
      protected val observable: FindPublisher[T],
      protected val queries: List[QueryCommand]
  ) extends FindQueryBuilder[F, T] {

    def first: F[Option[T]]                               = applyQueries().first().asyncSingle[F].map(Option.apply)
    def all: F[Iterable[T]]                               = applyQueries().asyncIterable[F]
    def stream: fs2.Stream[F, T]                          = applyQueries().stream[F]
    def boundedStream(capacity: Int): fs2.Stream[F, T]    = applyQueries().boundedStream[F](capacity)
    def explain: F[Document]                              = applyQueries().explain().asyncSingle[F].map(Document.fromJava)
    def explain(verbosity: ExplainVerbosity): F[Document] = applyQueries().explain(verbosity).asyncSingle[F].map(Document.fromJava)

    override protected def withQuery(command: QueryCommand): FindQueryBuilder[F, T] =
      Fs2FindQueryBuilder[F, T](observable, command :: queries)
  }

  final case class Fs2DistinctQueryBuilder[F[_]: Async, T: ClassTag](
      protected val observable: DistinctPublisher[T],
      protected val queries: List[QueryCommand]
  ) extends DistinctQueryBuilder[F, T] {

    def first: F[Option[T]]                            = applyQueries().first().asyncSingle[F].map(Option.apply)
    def all: F[Iterable[T]]                            = applyQueries().asyncIterable[F]
    def stream: fs2.Stream[F, T]                       = applyQueries().stream[F]
    def boundedStream(capacity: Int): fs2.Stream[F, T] = applyQueries().boundedStream[F](capacity)

    override protected def withQuery(command: QueryCommand): DistinctQueryBuilder[F, T] =
      Fs2DistinctQueryBuilder(observable, command :: queries)
  }

  final case class Fs2AggregateQueryBuilder[F[_]: Async, T: ClassTag](
      protected val observable: AggregatePublisher[T],
      protected val queries: List[QueryCommand]
  ) extends AggregateQueryBuilder[F, T] {

    def toCollection: F[Unit]                             = applyQueries().toCollection.asyncVoid[F]
    def first: F[Option[T]]                               = applyQueries().first().asyncSingle[F].map(Option.apply)
    def all: F[Iterable[T]]                               = applyQueries().asyncIterable[F]
    def stream: fs2.Stream[F, T]                          = applyQueries().stream[F]
    def boundedStream(capacity: Int): fs2.Stream[F, T]    = applyQueries().boundedStream[F](capacity)
    def explain: F[Document]                              = applyQueries().explain().asyncSingle[F].map(Document.fromJava)
    def explain(verbosity: ExplainVerbosity): F[Document] = applyQueries().explain(verbosity).asyncSingle[F].map(Document.fromJava)

    override protected def withQuery(command: QueryCommand): AggregateQueryBuilder[F, T] =
      Fs2AggregateQueryBuilder(observable, command :: queries)
  }
}
