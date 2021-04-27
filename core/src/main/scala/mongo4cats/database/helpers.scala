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

package mongo4cats.database

import cats.effect.Async
import cats.effect.std.{Dispatcher, Queue}
import org.mongodb.scala.{Observable, Observer, Subscription}
import org.reactivestreams.{Publisher, Subscriber, Subscription => RSSubscription}
import fs2.Stream

import scala.util.Either

private[database] object helpers {

  implicit final class ObservableOps[T](private val observable: Observable[T]) extends AnyVal {
    def void[F[_]: Async]: F[Unit] =
      Async[F].async_ { k =>
        observable.subscribe(new Observer[T] {
          override def onNext(result: T): Unit     = ()
          override def onError(e: Throwable): Unit = k(Left(e))
          override def onComplete(): Unit          = k(Right(()))
        })
      }

    def asyncIterable[F[_]: Async]: F[Iterable[T]] =
      Async[F].async_ { k =>
        observable.subscribe(new Observer[T] {
          private var results: List[T]             = Nil
          override def onNext(result: T): Unit     = results = result :: results
          override def onError(e: Throwable): Unit = k(Left(e))
          override def onComplete(): Unit          = k(Right(results.reverse))
        })
      }

    def asyncSingle[F[_]: Async]: F[T] =
      Async[F].async_ { k =>
        observable.subscribe(new Observer[T] {
          private var result: T                    = _
          override def onNext(res: T): Unit        = result = res
          override def onError(e: Throwable): Unit = k(Left(e))
          override def onComplete(): Unit          = k(Right(result))
        })
      }

    def stream[F[_]: Async]: Stream[F, T] =
      for {
        dispatcher <- Stream.resource(Dispatcher[F])
        queue      <- Stream.eval(Queue.unbounded[F, Option[Either[Throwable, T]]])
        _ = observable.subscribe(new Observer[T] {
          override def onNext(result: T): Unit =
            dispatcher.unsafeRunSync(queue.offer(Some(Right(result))))
          override def onError(e: Throwable): Unit =
            dispatcher.unsafeRunSync(queue.offer(Some(Left(e))))
          override def onComplete(): Unit =
            dispatcher.unsafeRunSync(queue.offer(None))
        })
        stream <- Stream.fromQueueNoneTerminated(queue).evalMap(Async[F].fromEither)
      } yield stream
  }

  def unicastPublisher[T](observable: Observable[T]): Publisher[T] = (s: Subscriber[_ >: T]) => {
    observable.subscribe(new Observer[T] {
      override def onSubscribe(sub: Subscription): Unit =
        s.onSubscribe(new RSSubscription {
          def request(n: Long): Unit = sub.request(n)
          def cancel(): Unit         = sub.unsubscribe()
        })
      def onNext(result: T): Unit     = s.onNext(result)
      def onError(e: Throwable): Unit = s.onError(e)
      def onComplete(): Unit          = s.onComplete()
    })
  }
}
