package mongo4cats.database

import org.mongodb.scala.{Observable, Observer, Subscription}
import org.reactivestreams.{Publisher, Subscriber, Subscription => RSSubscription}

import scala.util.Either

private[database] object helpers {

  def voidObserver(callback: Either[Throwable, Unit] => Unit): Observer[Void] =
    new Observer[Void] {

      override def onNext(res: Void): Unit = ()

      override def onError(e: Throwable): Unit =
        callback(Left(e))

      override def onComplete(): Unit =
        callback(Right(()))
    }

  def singleItemObserver[A](callback: Either[Throwable, A] => Unit): Observer[A] =
    new Observer[A] {
      private var result: A = _

      override def onNext(res: A): Unit =
        result = res

      override def onError(e: Throwable): Unit =
        callback(Left(e))

      override def onComplete(): Unit =
        callback(Right(result))
    }

  def multipleItemsObserver[A](callback: Either[Throwable, Iterable[A]] => Unit): Observer[A] =
    new Observer[A] {
      private var results: List[A] = Nil

      override def onNext(result: A): Unit =
        results = result :: results

      override def onError(e: Throwable): Unit =
        callback(Left(e))

      override def onComplete(): Unit =
        callback(Right(results.reverse))
    }

  def unicastPublisher[T](observable: Observable[T]): Publisher[T] = (s: Subscriber[_ >: T]) => {
    observable.subscribe(new Observer[T] {
      override def onSubscribe(sub: Subscription): Unit = {
        s.onSubscribe(new RSSubscription {
          def request(n: Long): Unit = sub.request(n)
          def cancel(): Unit = sub.unsubscribe()
        })
      }
      def onNext(result: T): Unit = s.onNext(result)
      def onError(e: Throwable): Unit = s.onError(e)
      def onComplete(): Unit = s.onComplete()
    })
  }
}
