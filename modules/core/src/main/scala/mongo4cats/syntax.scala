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
import mongo4cats.errors.MongoEmptyStreamException
import org.reactivestreams.{Publisher, Subscriber, Subscription}

import java.util.concurrent.atomic.AtomicReference
import scala.collection.mutable.{ListBuffer, Queue}
import scala.util.control.NonFatal

private[mongo4cats] object syntax {

  implicit final class OptionSyntax[F[_], T](private val fo: F[Option[T]]) extends AnyVal {
    def unNone(implicit F: Async[F]): F[T] =
      fo.flatMap(F.fromOption(_, MongoEmptyStreamException))
  }

  implicit final class PublisherSyntax[T](private val publisher: Publisher[T]) extends AnyVal {
    def asyncSingle[F[_]: Async]: F[Option[T]] =
      subscribe[F, Option[T]] { k =>
        new CancelableSubscriber[T](1) {
          private var result: Option[T]            = None
          override def onNext(res: T): Unit        = result = Option(res)
          override def onError(e: Throwable): Unit = k(Left(e))
          override def onComplete(): Unit          = k(Right(result))
        }
      }

    def asyncVoid[F[_]: Async]: F[Unit] =
      subscribe[F, Unit] { k =>
        new CancelableSubscriber[T](1) {
          override def onNext(result: T): Unit     = ()
          override def onError(e: Throwable): Unit = k(Left(e))
          override def onComplete(): Unit          = k(Right(()))
        }
      }

    def asyncIterable[F[_]: Async]: F[Iterable[T]] =
      asyncIterableF[F, T](identity)

    def asyncIterableF[F[_]: Async, Y](f: T => Y): F[Iterable[Y]] =
      subscribe[F, Iterable[Y]] { k =>
        new CancelableSubscriber[T](Long.MaxValue) {
          private val results: ListBuffer[Y]       = ListBuffer.empty[Y]
          override def onNext(result: T): Unit     = results += f(result)
          override def onError(e: Throwable): Unit = k(Left(e))
          override def onComplete(): Unit          = k(Right(results.toList))
        }
      }

    private def subscribe[F[_]: Async, A](mkSubscriber: (Either[Throwable, A] => Unit) => CancelableSubscriber[T]): F[A] =
      Async[F].async { callback =>
        Async[F].delay {
          val subscriber = mkSubscriber(callback)
          try publisher.subscribe(subscriber)
          catch {
            case NonFatal(error) =>
              subscriber.cancel()
              throw error
          }
          Some(Async[F].delay(subscriber.cancel()))
        }
      }

    def stream[F[_]: Async]: Stream[F, T] =
      boundedStream[F](1024)

    def boundedStream[F[_]: Async](capacity: Int): Stream[F, T] =
      Stream
        .bracket(Async[F].delay(new StreamSubscriber[T](capacity)))(s => Async[F].delay(s.cancel()))
        .flatMap { subscriber =>
          def consume: Stream[F, T] = {
            val request = if (capacity == 0) Stream.exec(Async[F].delay(subscriber.request(1))) else Stream.empty
            request ++ Stream.eval(subscriber.next[F]).flatMap {
              case Some(value) =>
                val replenish = if (capacity > 0) Stream.exec(Async[F].delay(subscriber.request(1))) else Stream.empty
                Stream.emit(value) ++ replenish ++ consume
              case None => Stream.empty
            }
          }

          Stream.exec(Async[F].delay(publisher.subscribe(subscriber))) ++ consume
        }
  }

  private object CanceledSubscription extends Subscription {
    override def request(n: Long): Unit = ()
    override def cancel(): Unit         = ()
  }

  abstract private class CancelableSubscriber[T](initialDemand: Long) extends Subscriber[T] {
    private val subscription     = new AtomicReference[Subscription]()
    private val subscriptionLock = new AnyRef
    private var pendingDemand    = initialDemand

    final override def onSubscribe(s: Subscription): Unit =
      if (subscription.compareAndSet(null, s)) request(0)
      else subscriptionLock.synchronized(s.cancel())

    final def request(n: Long): Unit = subscriptionLock.synchronized {
      val current = subscription.get()
      pendingDemand += n
      if (current != null) {
        val demand = pendingDemand
        pendingDemand = 0L
        if (demand > 0L) current.request(demand)
      }
    }

    final protected def isCanceled: Boolean = subscription.get() eq CanceledSubscription
    protected def clear(): Unit             = ()

    final def cancel(): Unit = {
      // Keep the canceled marker even when onSubscribe has not arrived yet.
      val current = subscription.getAndSet(CanceledSubscription)
      clear()
      if (current != null && current != CanceledSubscription) subscriptionLock.synchronized(current.cancel())
    }
  }

  final private class StreamSubscriber[T](capacity: Int) extends CancelableSubscriber[T](capacity.toLong) {
    require(capacity >= 0, "Stream capacity must be non-negative")

    private type Signal = Either[Throwable, Option[T]]
    // Demand bounds the data to capacity (one requested element for rendezvous); terminal signals need no demand.
    // Callbacks only enqueue or resume a waiter and never block waiting for consumption.
    private val pending                         = Queue.empty[Signal]
    private var waiting: Option[Signal => Unit] = None
    private var done                            = false

    override def onNext(value: T): Unit          = offer(Right(Some(value)), terminal = false)
    override def onError(error: Throwable): Unit = offer(Left(error), terminal = true)
    override def onComplete(): Unit              = offer(Right(None), terminal = true)

    private def offer(signal: Signal, terminal: Boolean): Unit = {
      val callback = synchronized {
        if (isCanceled || done) None
        else {
          done = terminal
          val callback = waiting
          waiting = None
          if (callback.isEmpty) pending.enqueue(signal)
          callback
        }
      }
      callback.foreach(_(signal))
    }

    def next[F[_]: Async]: F[Option[T]] =
      Async[F].async { callback =>
        Async[F].delay {
          val signal = synchronized {
            if (pending.nonEmpty) Some(pending.dequeue())
            else {
              waiting = Some(callback)
              None
            }
          }
          signal.foreach(callback)
          // The enclosing stream bracket owns subscription cancellation and clears the waiter.
          Some(Async[F].unit)
        }
      }

    override protected def clear(): Unit = synchronized {
      pending.clear()
      waiting = None
    }
  }
}
