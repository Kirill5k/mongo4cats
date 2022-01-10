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
import fs2.Stream
import fs2.interop.reactivestreams
import org.reactivestreams.{Publisher, Subscriber, Subscription}

import scala.collection.mutable.ListBuffer
import scala.reflect.ClassTag

private[mongo4cats] object helpers {

  def clazz[Y: ClassTag]: Class[Y] =
    implicitly[ClassTag[Y]].runtimeClass.asInstanceOf[Class[Y]]

  implicit final class PublisherOps[T](private val publisher: Publisher[T]) extends AnyVal {
    def asyncSingle[F[_]: Async]: F[T] =
      Async[F].async_ { k =>
        publisher.subscribe(new Subscriber[T] {
          private var result: T                  = _
          private var subscription: Subscription = null

          override def onNext(res: T): Unit =
            result = res

          override def onError(e: Throwable): Unit = {
            subscription.cancel()
            k(Left(e))
          }

          override def onComplete(): Unit = {
            subscription.cancel()
            k(Right(result))
          }

          override def onSubscribe(s: Subscription): Unit = {
            subscription = s
            subscription.request(1)
          }
        })
      }

    def asyncVoid[F[_]: Async]: F[Unit] =
      Async[F].async_ { k =>
        publisher.subscribe(new Subscriber[T] {
          private var subscription: Subscription = null

          override def onNext(result: T): Unit = ()

          override def onError(e: Throwable): Unit = {
            subscription.cancel()
            k(Left(e))
          }

          override def onComplete(): Unit = {
            subscription.cancel()
            k(Right(()))
          }

          override def onSubscribe(s: Subscription): Unit = {
            subscription = s
            subscription.request(1)
          }
        })
      }

    def asyncIterable[F[_]: Async]: F[Iterable[T]] =
      Async[F].async_ { k =>
        publisher.subscribe(new Subscriber[T] {
          private val results: ListBuffer[T]     = ListBuffer.empty[T]
          private var subscription: Subscription = null

          override def onSubscribe(s: Subscription): Unit = {
            subscription = s
            subscription.request(Long.MaxValue)
          }

          override def onNext(result: T): Unit =
            results += result

          override def onError(e: Throwable): Unit = {
            subscription.cancel()
            k(Left(e))
          }

          override def onComplete(): Unit = {
            subscription.cancel()
            k(Right(results.toList))
          }
        })
      }
    def stream[F[_]: Async]: Stream[F, T] =
      reactivestreams.fromPublisher(publisher, Int.MaxValue)

    def boundedStream[F[_]: Async](capacity: Int): Stream[F, T] =
      reactivestreams.fromPublisher(publisher, capacity)
  }
}
