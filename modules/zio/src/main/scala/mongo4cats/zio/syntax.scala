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

import mongo4cats.errors.MongoEmptyStreamException
import org.reactivestreams.{Publisher, Subscriber, Subscription}
import zio.interop.reactivestreams._
import zio.stream.Stream
import zio.{Task, ZIO}

import scala.collection.mutable.ListBuffer

private[zio] object syntax {

  implicit final class TaskOptionSyntax[T](private val task: Task[Option[T]]) extends AnyVal {
    def unNone: Task[T] = task.map(_.toRight(MongoEmptyStreamException)).flatMap(ZIO.fromEither(_))
  }

  // TODO: Replace ZIO.async call with ZIO.greenThreadOrElse in ZIO 2.1

  implicit final class PublisherSyntax[T](private val publisher: Publisher[T]) extends AnyVal {
    def asyncVoid: Task[Unit] = ZIO.async { callback =>
      publisher.subscribe(new Subscriber[T] {
        override def onSubscribe(s: Subscription): Unit = s.request(1)
        override def onNext(t: T): Unit                 = ()
        override def onError(t: Throwable): Unit        = callback(ZIO.fail(t))
        override def onComplete(): Unit                 = callback(ZIO.unit)
      })
    }

    def asyncSingle: Task[Option[T]] = ZIO.async { callback =>
      publisher.subscribe(new Subscriber[T] {
        private var result: Option[T]                   = None
        override def onSubscribe(s: Subscription): Unit = s.request(1)
        override def onNext(t: T): Unit                 = result = Option(t)
        override def onError(t: Throwable): Unit        = callback(ZIO.fail(t))
        override def onComplete(): Unit                 = callback(ZIO.succeed(result))
      })
    }

    def asyncIterable: Task[Iterable[T]] = asyncIterableF(identity)

    def asyncIterableF[Y](f: T => Y): Task[Iterable[Y]] = ZIO.async { callback =>
      publisher.subscribe(new Subscriber[T] {
        private val result: ListBuffer[Y]               = ListBuffer.empty
        override def onSubscribe(s: Subscription): Unit = s.request(Long.MaxValue)
        override def onNext(t: T): Unit                 = result += f(t)
        override def onError(t: Throwable): Unit        = callback(ZIO.fail(t))
        override def onComplete(): Unit                 = callback(ZIO.succeed(result.toList))
      })
    }

    def stream: Stream[Throwable, T]                       = publisher.toZIOStream(512)
    def boundedStream(capacity: Int): Stream[Throwable, T] = publisher.toZIOStream(capacity)
  }
}
