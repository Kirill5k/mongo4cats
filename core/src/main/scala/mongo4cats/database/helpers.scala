package mongo4cats.database

import cats.effect.Concurrent
import fs2.concurrent.NoneTerminatedQueue
import mongo4cats.errors.OperationError
import org.mongodb.scala.Observer

import scala.util.Either

private[database] object helpers {

  def singleItemObserver[A](callback: Either[Throwable, A] => Unit): Observer[A] =
    new Observer[A] {
      private var result: A = _

      override def onNext(res: A): Unit =
        result = res

      override def onError(e: Throwable): Unit =
        callback(Left(OperationError(e.getMessage)))

      override def onComplete(): Unit =
        callback(Right(result))
    }

  def multipleItemsObserver[A](callback: Either[Throwable, Iterable[A]] => Unit): Observer[A] =
    new Observer[A] {
      private var results: List[A] = Nil

      override def onNext(result: A): Unit =
        results = result :: results

      override def onError(e: Throwable): Unit =
        callback(Left(OperationError(e.getMessage)))

      override def onComplete(): Unit =
        callback(Right(results.reverse))
    }

  def streamObserver[F[_]: Concurrent, A](queue: NoneTerminatedQueue[F, A]): Observer[A] =
    new Observer[A] {
      override def onNext(result: A): Unit = {
        queue.enqueue(fs2.Stream.emit(Some(result)))
        ()
      }

      override def onError(e: Throwable): Unit = {
        queue.enqueue(fs2.Stream.raiseError(OperationError(e.getMessage)))
        ()
      }

      override def onComplete(): Unit = {
        queue.enqueue(fs2.Stream.emit(None))
        ()
      }
    }
}
