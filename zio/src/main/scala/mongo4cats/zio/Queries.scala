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

package mongo4cats.zio

import com.mongodb.ExplainVerbosity
import com.mongodb.client.model.changestream.ChangeStreamDocument
import com.mongodb.reactivestreams.client.{AggregatePublisher, ChangeStreamPublisher, DistinctPublisher, FindPublisher}
import mongo4cats.bson.Document
import mongo4cats.queries.{AggregateQueryBuilder, DistinctQueryBuilder, FindQueryBuilder, QueryCommand, WatchQueryBuilder}
import mongo4cats.zio.syntax._
import zio.stream.Stream
import zio.Task

import scala.reflect.ClassTag

private[zio] object Queries {
  type Aggregate[T] = AggregateQueryBuilder[Task, T, Stream[Throwable, *]]
  type Watch[T]     = WatchQueryBuilder[Task, T, Stream[Throwable, *]]
  type Find[T]      = FindQueryBuilder[Task, T, Stream[Throwable, *]]
  type Distinct[T]  = DistinctQueryBuilder[Task, T, Stream[Throwable, *]]

  def watch[T: ClassTag](observable: ChangeStreamPublisher[T]): Watch[T]      = ZWatchQueryBuilder(observable, Nil)
  def find[T: ClassTag](observable: FindPublisher[T]): Find[T]                = ZFindQueryBuilder(observable, Nil)
  def distinct[T: ClassTag](observable: DistinctPublisher[T]): Distinct[T]    = ZDistinctQueryBuilder(observable, Nil)
  def aggregate[T: ClassTag](observable: AggregatePublisher[T]): Aggregate[T] = ZAggregateQueryBuilder(observable, Nil)

  final private case class ZWatchQueryBuilder[T: ClassTag](
      protected val observable: ChangeStreamPublisher[T],
      protected val queries: List[QueryCommand]
  ) extends WatchQueryBuilder[Task, T, Stream[Throwable, *]] {

    def stream: Stream[Throwable, ChangeStreamDocument[T]]                       = applyQueries().stream
    def boundedStream(capacity: Int): Stream[Throwable, ChangeStreamDocument[T]] = applyQueries().boundedStream(capacity)

    override protected def withQuery(command: QueryCommand): Watch[T] = ZWatchQueryBuilder(observable, command :: queries)
  }

  final private case class ZFindQueryBuilder[T: ClassTag](
      protected val observable: FindPublisher[T],
      protected val queries: List[QueryCommand]
  ) extends FindQueryBuilder[Task, T, Stream[Throwable, *]] {

    def first: Task[Option[T]]                               = applyQueries().first().asyncSingle
    def all: Task[Iterable[T]]                               = applyQueries().asyncIterable
    def stream: Stream[Throwable, T]                         = applyQueries().stream
    def boundedStream(capacity: Int): Stream[Throwable, T]   = applyQueries().boundedStream(capacity)
    def explain: Task[Document]                              = applyQueries().explain().asyncSingle.unNone.map(Document.fromJava)
    def explain(verbosity: ExplainVerbosity): Task[Document] = applyQueries().explain(verbosity).asyncSingle.unNone.map(Document.fromJava)

    override protected def withQuery(command: QueryCommand): Find[T] = ZFindQueryBuilder[T](observable, command :: queries)
  }

  final private case class ZDistinctQueryBuilder[T: ClassTag](
      protected val observable: DistinctPublisher[T],
      protected val queries: List[QueryCommand]
  ) extends DistinctQueryBuilder[Task, T, Stream[Throwable, *]] {

    def first: Task[Option[T]]                             = applyQueries().first().asyncSingle
    def all: Task[Iterable[T]]                             = applyQueries().asyncIterable
    def stream: Stream[Throwable, T]                       = applyQueries().stream
    def boundedStream(capacity: Int): Stream[Throwable, T] = applyQueries().boundedStream(capacity)

    override protected def withQuery(command: QueryCommand): Distinct[T] = ZDistinctQueryBuilder(observable, command :: queries)
  }

  final private case class ZAggregateQueryBuilder[T: ClassTag](
      protected val observable: AggregatePublisher[T],
      protected val queries: List[QueryCommand]
  ) extends AggregateQueryBuilder[Task, T, Stream[Throwable, *]] {

    def toCollection: Task[Unit]                             = applyQueries().toCollection.asyncVoid
    def first: Task[Option[T]]                               = applyQueries().first().asyncSingle
    def all: Task[Iterable[T]]                               = applyQueries().asyncIterable
    def stream: Stream[Throwable, T]                         = applyQueries().stream
    def boundedStream(capacity: Int): Stream[Throwable, T]   = applyQueries().boundedStream(capacity)
    def explain: Task[Document]                              = applyQueries().explain().asyncSingle.unNone.map(Document.fromJava)
    def explain(verbosity: ExplainVerbosity): Task[Document] = applyQueries().explain(verbosity).asyncSingle.unNone.map(Document.fromJava)

    override protected def withQuery(command: QueryCommand): Aggregate[T] = ZAggregateQueryBuilder(observable, command :: queries)
  }
}
