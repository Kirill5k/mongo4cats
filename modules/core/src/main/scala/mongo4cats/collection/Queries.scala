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

package mongo4cats.collection

import cats.effect.Async
import cats.syntax.functor._
import com.mongodb.ExplainVerbosity
import com.mongodb.reactivestreams.client.{AggregatePublisher, ChangeStreamPublisher, DistinctPublisher, FindPublisher}
import mongo4cats.bson.Document
import mongo4cats.queries._
import mongo4cats.syntax._
import fs2.Stream
import mongo4cats.models.collection.ChangeStreamDocument

import scala.reflect.ClassTag

private[collection] object Queries {
  type Aggregate[F[_], T] = AggregateQueryBuilder[F, T, Stream[F, *]]
  type Watch[F[_], T]     = WatchQueryBuilder[F, T, Stream[F, *]]
  type Find[F[_], T]      = FindQueryBuilder[F, T, Stream[F, *]]
  type Distinct[F[_], T]  = DistinctQueryBuilder[F, T, Stream[F, *]]

  def watch[F[_]: Async, T: ClassTag](observable: => ChangeStreamPublisher[T]): Watch[F, T] =
    Fs2WatchQueryBuilder(() => observable, Nil)

  def find[F[_]: Async, T: ClassTag](observable: => FindPublisher[T]): Find[F, T] =
    Fs2FindQueryBuilder(() => observable, Nil)

  def distinct[F[_]: Async, T: ClassTag](observable: => DistinctPublisher[T]): Distinct[F, T] =
    Fs2DistinctQueryBuilder(() => observable, Nil)

  def aggregate[F[_]: Async, T: ClassTag](observable: => AggregatePublisher[T]): Aggregate[F, T] =
    Fs2AggregateQueryBuilder(() => observable, Nil)

  final private case class Fs2WatchQueryBuilder[F[_]: Async, T: ClassTag](
      publisherFactory: () => ChangeStreamPublisher[T],
      protected val queries: List[QueryCommand]
  ) extends WatchQueryBuilder[F, T, Stream[F, *]] {

    override protected def observable: ChangeStreamPublisher[T] = publisherFactory()

    def stream: Stream[F, ChangeStreamDocument[T]] =
      Stream.eval(Async[F].delay(applyQueries())).flatMap(_.stream[F]).map(ChangeStreamDocument.fromJava)
    def boundedStream(capacity: Int): Stream[F, ChangeStreamDocument[T]] =
      Stream.eval(Async[F].delay(applyQueries())).flatMap(_.boundedStream[F](capacity)).map(ChangeStreamDocument.fromJava)

    override protected def withQuery(command: QueryCommand): Watch[F, T] = Fs2WatchQueryBuilder(publisherFactory, command :: queries)
  }

  final private case class Fs2FindQueryBuilder[F[_]: Async, T: ClassTag](
      publisherFactory: () => FindPublisher[T],
      protected val queries: List[QueryCommand]
  ) extends FindQueryBuilder[F, T, Stream[F, *]] {

    override protected def observable: FindPublisher[T] = publisherFactory()

    def first: F[Option[T]]                        = Async[F].defer(applyQueries().first().asyncSingle[F])
    def all: F[Iterable[T]]                        = Async[F].defer(applyQueries().asyncIterable[F])
    def stream: Stream[F, T]                       = Stream.eval(Async[F].delay(applyQueries())).flatMap(_.stream[F])
    def boundedStream(capacity: Int): Stream[F, T] = Stream.eval(Async[F].delay(applyQueries())).flatMap(_.boundedStream[F](capacity))
    def explain: F[Document]                       = Async[F].defer(applyQueries().explain().asyncSingle[F].unNone.map(Document.fromJava))
    def explain(verbosity: ExplainVerbosity): F[Document] =
      Async[F].defer(applyQueries().explain(verbosity).asyncSingle[F].unNone.map(Document.fromJava))

    override protected def withQuery(command: QueryCommand): Find[F, T] = Fs2FindQueryBuilder[F, T](publisherFactory, command :: queries)
  }

  final private case class Fs2DistinctQueryBuilder[F[_]: Async, T: ClassTag](
      publisherFactory: () => DistinctPublisher[T],
      protected val queries: List[QueryCommand]
  ) extends DistinctQueryBuilder[F, T, Stream[F, *]] {

    override protected def observable: DistinctPublisher[T] = publisherFactory()

    def first: F[Option[T]]                        = Async[F].defer(applyQueries().first().asyncSingle[F])
    def all: F[Iterable[T]]                        = Async[F].defer(applyQueries().asyncIterable[F])
    def stream: Stream[F, T]                       = Stream.eval(Async[F].delay(applyQueries())).flatMap(_.stream[F])
    def boundedStream(capacity: Int): Stream[F, T] = Stream.eval(Async[F].delay(applyQueries())).flatMap(_.boundedStream[F](capacity))

    override protected def withQuery(command: QueryCommand): Distinct[F, T] = Fs2DistinctQueryBuilder(publisherFactory, command :: queries)
  }

  final private case class Fs2AggregateQueryBuilder[F[_]: Async, T: ClassTag](
      publisherFactory: () => AggregatePublisher[T],
      protected val queries: List[QueryCommand]
  ) extends AggregateQueryBuilder[F, T, Stream[F, *]] {

    override protected def observable: AggregatePublisher[T] = publisherFactory()

    def toCollection: F[Unit]                      = Async[F].defer(applyQueries().toCollection.asyncVoid[F])
    def first: F[Option[T]]                        = Async[F].defer(applyQueries().first().asyncSingle[F])
    def all: F[Iterable[T]]                        = Async[F].defer(applyQueries().asyncIterable[F])
    def stream: Stream[F, T]                       = Stream.eval(Async[F].delay(applyQueries())).flatMap(_.stream[F])
    def boundedStream(capacity: Int): Stream[F, T] = Stream.eval(Async[F].delay(applyQueries())).flatMap(_.boundedStream[F](capacity))
    def explain: F[Document]                       = Async[F].defer(applyQueries().explain().asyncSingle[F].unNone.map(Document.fromJava))
    def explain(verbosity: ExplainVerbosity): F[Document] =
      Async[F].defer(applyQueries().explain(verbosity).asyncSingle[F].unNone.map(Document.fromJava))

    override protected def withQuery(command: QueryCommand): Aggregate[F, T] =
      Fs2AggregateQueryBuilder(publisherFactory, command :: queries)
  }
}
