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

package mongo4cats

import cats.effect.Async
import cats.syntax.flatMap._
import fs2.Stream
import fs2.interop.reactivestreams._
import mongo4cats.errors.MongoEmptyStreamException
import org.reactivestreams.{Publisher, Subscriber, Subscription}

import scala.collection.mutable.ListBuffer

private[mongo4cats] object syntax {

  implicit final class OptionSyntax[F[_], T](private val fo: F[Option[T]]) extends AnyVal {
    def unNone(implicit F: Async[F]): F[T] =
      fo.flatMap(F.fromOption(_, MongoEmptyStreamException))
  }

  implicit final class PublisherSyntax[T](private val publisher: Publisher[T]) extends AnyVal {
    def asyncSingle[F[_]: Async]: F[Option[T]] =
      Async[F].async_ { k =>
        publisher.subscribe(new Subscriber[T] {
          private var result: Option[T]                   = None
          override def onNext(res: T): Unit               = result = Option(res)
          override def onError(e: Throwable): Unit        = k(Left(e))
          override def onComplete(): Unit                 = k(Right(result))
          override def onSubscribe(s: Subscription): Unit = s.request(1)
        })
      }

    def asyncVoid[F[_]: Async]: F[Unit] =
      Async[F].async_ { k =>
        publisher.subscribe(new Subscriber[T] {
          override def onNext(result: T): Unit            = ()
          override def onError(e: Throwable): Unit        = k(Left(e))
          override def onComplete(): Unit                 = k(Right(()))
          override def onSubscribe(s: Subscription): Unit = s.request(1)
        })
      }

    def asyncIterable[F[_]: Async]: F[Iterable[T]] =
      Async[F].async_ { k =>
        publisher.subscribe(new Subscriber[T] {
          private val results: ListBuffer[T]              = ListBuffer.empty[T]
          override def onSubscribe(s: Subscription): Unit = s.request(Long.MaxValue)
          override def onNext(result: T): Unit            = results += result
          override def onError(e: Throwable): Unit        = k(Left(e))
          override def onComplete(): Unit                 = k(Right(results.toList))
        })
      }

    def stream[F[_]: Async]: Stream[F, T] =
      boundedStream(1024)

    def boundedStream[F[_]: Async](capacity: Int): Stream[F, T] =
      publisher.toStreamBuffered(capacity)

  }
}
