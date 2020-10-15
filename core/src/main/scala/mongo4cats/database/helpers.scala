/*
 * Copyright 2020 Mongo DB client wrapper for Cats Effect & FS2
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

import org.mongodb.scala.{Observable, Observer, Subscription}
import org.reactivestreams.{Publisher, Subscriber, Subscription => RSSubscription}

import scala.util.Either

private[database] object helpers {

  def voidAsync(observable: Observable[Void]): (Either[Throwable, Unit] => Unit) => Unit = k => {
    observable.subscribe(new Observer[Void] {

      override def onNext(res: Void): Unit = ()

      override def onError(e: Throwable): Unit =
        k(Left(e))

      override def onComplete(): Unit =
        k(Right(()))
    })
  }

  def multipleItemsAsync[T](observable: Observable[T]): (Either[Throwable, Iterable[T]] => Unit) => Unit = k => {
    observable.subscribe(new Observer[T] {
      private var results: List[T] = Nil

      override def onNext(result: T): Unit =
        results = result :: results

      override def onError(e: Throwable): Unit =
        k(Left(e))

      override def onComplete(): Unit =
        k(Right(results.reverse))
    })
  }

  def singleItemAsync[T](observable: Observable[T]): (Either[Throwable, T] => Unit) => Unit = k => {
    observable.subscribe(new Observer[T] {
      private var result: T = _

      override def onNext(res: T): Unit =
        result = res

      override def onError(e: Throwable): Unit =
        k(Left(e))

      override def onComplete(): Unit =
        k(Right(result))
    })
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
