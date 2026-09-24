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

import java.util.concurrent.atomic.AtomicReference
import scala.collection.mutable.ListBuffer
import scala.util.control.NonFatal

private[zio] object syntax {

  implicit final class TaskOptionSyntax[T](private val task: Task[Option[T]]) extends AnyVal {
    def unNone: Task[T] = task.map(_.toRight(MongoEmptyStreamException)).flatMap(ZIO.fromEither(_))
  }

  private object CancelledSubscription extends Subscription {
    override def request(n: Long): Unit = ()
    override def cancel(): Unit         = ()
  }

  abstract private class CancelableSubscriber[T](demand: Long) extends Subscriber[T] {
    private val subscription     = new AtomicReference[Subscription]()
    private val subscriptionLock = new Object

    final override def onSubscribe(s: Subscription): Unit =
      if (subscription.compareAndSet(null, s)) {
        subscriptionLock.synchronized {
          if (subscription.get() != CancelledSubscription) s.request(demand)
        }
      } else subscriptionLock.synchronized(s.cancel())

    final def cancel(): Unit = {
      // Keep the cancelled state even when onSubscribe has not arrived yet.
      val previous = subscription.getAndSet(CancelledSubscription)
      if (previous != null && previous != CancelledSubscription) subscriptionLock.synchronized(previous.cancel())
    }
  }

  private def subscribe[T](publisher: Publisher[T], subscriber: CancelableSubscriber[T]): Unit =
    try publisher.subscribe(subscriber)
    catch {
      case NonFatal(error) =>
        subscriber.cancel()
        throw error
    }

  implicit final class PublisherSyntax[T](private val publisher: Publisher[T]) extends AnyVal {
    def asyncVoid: Task[Unit] = ZIO.asyncInterrupt { callback =>
      val subscriber = new CancelableSubscriber[T](1) {
        override def onNext(t: T): Unit          = ()
        override def onError(t: Throwable): Unit = callback(ZIO.fail(t))
        override def onComplete(): Unit          = callback(ZIO.unit)
      }
      subscribe(publisher, subscriber)
      Left(ZIO.succeed(subscriber.cancel()))
    }

    def asyncSingle: Task[Option[T]] = ZIO.asyncInterrupt { callback =>
      val subscriber = new CancelableSubscriber[T](1) {
        private var result: Option[T]            = None
        override def onNext(t: T): Unit          = result = Option(t)
        override def onError(t: Throwable): Unit = callback(ZIO.fail(t))
        override def onComplete(): Unit          = callback(ZIO.succeed(result))
      }
      subscribe(publisher, subscriber)
      Left(ZIO.succeed(subscriber.cancel()))
    }

    def asyncIterable: Task[Iterable[T]] = asyncIterableF(identity)

    def asyncIterableF[Y](f: T => Y): Task[Iterable[Y]] = ZIO.asyncInterrupt { callback =>
      val subscriber = new CancelableSubscriber[T](Long.MaxValue) {
        private val result: ListBuffer[Y]        = ListBuffer.empty
        override def onNext(t: T): Unit          = result += f(t)
        override def onError(t: Throwable): Unit = callback(ZIO.fail(t))
        override def onComplete(): Unit          = callback(ZIO.succeed(result.toList))
      }
      subscribe(publisher, subscriber)
      Left(ZIO.succeed(subscriber.cancel()))
    }

    def stream: Stream[Throwable, T]                       = publisher.toZIOStream(512)
    def boundedStream(capacity: Int): Stream[Throwable, T] = publisher.toZIOStream(capacity)
  }
}
