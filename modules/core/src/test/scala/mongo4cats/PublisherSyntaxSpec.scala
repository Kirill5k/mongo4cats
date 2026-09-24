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

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.applicativeError._
import cats.syntax.either._
import cats.syntax.traverse._
import mongo4cats.syntax._
import org.reactivestreams.{Publisher, Subscriber, Subscription}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import java.util.concurrent.{ConcurrentLinkedQueue, CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger, AtomicLong, AtomicReference}
import scala.concurrent.duration._

class PublisherSyntaxSpec extends AsyncWordSpec with Matchers {

  sealed trait Action[+T]              extends Serializable with Product
  case class OnNext[T](element: T)     extends Action[T]
  case class OnError(error: Throwable) extends Action[Nothing]
  case object OnComplete               extends Action[Nothing]

  private val adapters: List[(String, Publisher[String] => IO[Unit])] = List(
    "asyncSingle"          -> ((p: Publisher[String]) => p.asyncSingle[IO].void),
    "asyncVoid"            -> ((p: Publisher[String]) => p.asyncVoid[IO]),
    "asyncIterable"        -> ((p: Publisher[String]) => p.asyncIterable[IO].void),
    "asyncIterableF"       -> ((p: Publisher[String]) => p.asyncIterableF[IO, Int](_.length).void),
    "stream"               -> ((p: Publisher[String]) => p.stream[IO].compile.drain),
    "zero-capacity stream" -> ((p: Publisher[String]) => p.boundedStream[IO](0).compile.drain)
  )

  "A PublisherSyntax" when {

    adapters.foreach { case (name, adapt) =>
      name should {
        "cancel an active driver subscription without waiting for completion" in {
          val source = new ControlledPublisher(delayedSubscription = false)

          (for {
            fiber        <- adapt(source).start
            _            <- source.awaitRequest
            cancellation <- fiber.cancel.start
            _            <- cancellation.joinWithNever.timeout(2.seconds)
          } yield source.cancelCalls.get() mustBe 1)
            .guarantee(IO(source.finish()))
            .unsafeToFuture()
        }

        "cancel a subscription delivered after cancellation without requesting elements" in {
          val source = new ControlledPublisher(delayedSubscription = true)

          (for {
            fiber        <- adapt(source).start
            _            <- source.awaitSubscription
            cancellation <- fiber.cancel.start
            _            <- cancellation.joinWithNever.timeout(2.seconds)
            _            <- IO(source.deliverSubscription())
          } yield {
            source.cancelCalls.get() mustBe 1
            source.requested.get() mustBe 0L
          }).guarantee(IO(source.finish())).unsafeToFuture()
        }

        "cancel duplicate subscriptions without requesting from them" in {
          val source            = new ControlledPublisher(delayedSubscription = false)
          val duplicateRequests = new AtomicLong(0L)
          val duplicateCancels  = new AtomicInteger(0)
          val duplicate         = new Subscription {
            override def request(n: Long): Unit = {
              duplicateRequests.addAndGet(n)
              ()
            }
            override def cancel(): Unit = {
              duplicateCancels.incrementAndGet()
              ()
            }
          }

          (for {
            fiber        <- adapt(source).start
            _            <- source.awaitRequest
            _            <- IO(source.deliverAdditionalSubscription(duplicate))
            cancellation <- fiber.cancel.start
            _            <- cancellation.joinWithNever.timeout(2.seconds)
          } yield {
            duplicateRequests.get() mustBe 0L
            duplicateCancels.get() mustBe 1
            source.cancelCalls.get() mustBe 1
          }).guarantee(IO(source.finish())).unsafeToFuture()
        }

        "propagate a publisher error" in {
          val error = new RuntimeException("uh-oh")

          adapt(publisher(List(OnError(error)))).attempt.unsafeToFuture().map(_ mustBe Left(error))
        }
      }
    }

    "asyncSingle" should {
      "return the element only when the publisher completes" in {
        val source = new ControlledPublisher(delayedSubscription = false)

        (for {
          fiber   <- source.asyncSingle[IO].start
          _       <- source.awaitRequest
          _       <- IO(source.emit("a"))
          pending <- IO.race(fiber.joinWithNever, IO.sleep(100.millis))
          _       <- IO(source.finish())
          result  <- fiber.joinWithNever
        } yield {
          pending mustBe Right(())
          result mustBe Some("a")
        }).guarantee(IO(source.finish())).unsafeToFuture()
      }

      "return None for an empty publisher" in
        publisher(List(OnComplete)).asyncSingle[IO].unsafeToFuture().map(_ mustBe None)
    }

    "asyncVoid" should {
      "wait for publisher completion after receiving an element" in {
        val source = new ControlledPublisher(delayedSubscription = false)

        (for {
          fiber   <- source.asyncVoid[IO].start
          _       <- source.awaitRequest
          _       <- IO(source.emit("a"))
          pending <- IO.race(fiber.joinWithNever, IO.sleep(100.millis))
          _       <- IO(source.finish())
          _       <- fiber.joinWithNever
        } yield pending mustBe Right(())).guarantee(IO(source.finish())).unsafeToFuture()
      }
    }

    "asyncIterable" should {
      "collect every element in order" in
        publisher(List(OnNext("a"), OnNext("b"), OnNext("c"), OnComplete))
          .asyncIterable[IO]
          .unsafeToFuture()
          .map(_.toList mustBe List("a", "b", "c"))

      "return an empty collection for an empty publisher" in
        publisher(List(OnComplete)).asyncIterable[IO].unsafeToFuture().map(_.toList mustBe Nil)
    }

    "asyncIterableF" should {
      "transform every element in order" in
        publisher(List(OnNext("a"), OnNext("bb"), OnNext("ccc"), OnComplete))
          .asyncIterableF[IO, Int](_.length)
          .unsafeToFuture()
          .map(_.toList mustBe List(1, 2, 3))

      "cancel the subscription when synchronous element transformation fails" in {
        val error  = new RuntimeException("transformation failed")
        val source = publisher(List(OnNext("a"), OnNext("b"), OnComplete))

        source
          .asyncIterableF[IO, Int](_ => throw error)
          .attempt
          .unsafeToFuture()
          .map { result =>
            result mustBe Left(error)
            source.cancelCalls.get() mustBe 1
          }
      }
    }

    "stream" should {
      "convert elements into a stream" in
        publisher(List(OnNext("a"), OnNext("b"), OnNext("c"), OnComplete))
          .stream[IO]
          .compile
          .toList
          .unsafeToFuture()
          .map(_ mustBe List("a", "b", "c"))

      "process errors after all preceding elements" in {
        val error = new RuntimeException("uh-oh")

        publisher(List(OnNext("a"), OnNext("b"), OnNext("c"), OnError(error)))
          .stream[IO]
          .map[Either[Throwable, String]](s => Right(s))
          .handleError(e => Left(e))
          .compile
          .toList
          .unsafeToFuture()
          .map(_ mustBe List(Right("a"), Right("b"), Right("c"), Left(error)))
      }

      "cancel upstream after take(1) without requiring a terminal signal" in {
        val source = publisher(List(OnNext("a"), OnNext("b")))

        source.stream[IO].take(1).compile.toList.timeout(2.seconds).unsafeToFuture().map { result =>
          result mustBe List("a")
          source.cancelCalls.get() mustBe 1
        }
      }

      "cancel upstream when downstream processing fails" in {
        val source = publisher(List(OnNext("a"), OnNext("b")))
        val error  = new RuntimeException("downstream failed")

        source.stream[IO].evalMap(_ => IO.raiseError[Unit](error)).compile.drain.attempt.unsafeToFuture().map { result =>
          result mustBe Left(error)
          source.cancelCalls.get() mustBe 1
        }
      }

      "cancel upstream when a stream without a terminal signal times out" in {
        val source = publisher(List(OnNext("a"), OnNext("b")))

        source.stream[IO].timeout(100.millis).compile.toList.attempt.unsafeToFuture().map { result =>
          result.leftMap(_.getMessage) mustBe Left(s"Timed out after ${100.millis}")
          source.cancelCalls.get() mustBe 1
        }
      }

      "create a fresh subscription when the same stream is compiled again" in {
        val stream = publisher(List(OnNext("a"), OnNext("b"), OnComplete)).stream[IO]

        (for {
          first  <- stream.compile.toList
          second <- stream.compile.toList
        } yield {
          first mustBe List("a", "b")
          second mustBe first
        }).unsafeToFuture()
      }

      "complete for an empty publisher" in
        publisher(List(OnComplete)).stream[IO].compile.toList.unsafeToFuture().map(_ mustBe Nil)
    }

    "boundedStream" should {
      "reject negative buffer capacities" in {
        val source = publisher(List(OnComplete))

        List(-1, -2).traverse(capacity => source.boundedStream[IO](capacity).compile.drain.attempt).unsafeToFuture().map { results =>
          results.foreach {
            case Left(_: IllegalArgumentException) => ()
            case result                            => fail(s"Expected invalid capacity to fail, got $result")
          }
          succeed
        }
      }

      "request one element at a time with zero buffer capacity" in {
        val source = new ScriptedPublisher(List(OnNext("a"), OnNext("b"), OnComplete), maxRequest = 1L)

        source.boundedStream[IO](0).compile.toList.timeout(2.seconds).unsafeToFuture().map(_ mustBe List("a", "b"))
      }

      "retain zero-capacity demand until a delayed subscription arrives" in {
        val source = new ControlledPublisher(delayedSubscription = true)

        (for {
          fiber  <- source.boundedStream[IO](0).take(1).compile.toList.start
          _      <- source.awaitSubscription
          _      <- IO(source.deliverSubscription())
          _      <- source.awaitRequest
          _      <- IO(source.emit("a"))
          result <- fiber.joinWithNever.timeout(2.seconds)
        } yield {
          result mustBe List("a")
          source.requested.get() mustBe 1L
          source.cancelCalls.get() mustBe 1
        }).guarantee(IO(source.finish())).unsafeToFuture()
      }

      "drain a synchronous publisher larger than its buffer using bounded demand" in {
        val elements = (1 to 65).map(_.toString).toList
        // Reject excessive demand before emitting: the previous bridge otherwise deadlocks its synchronous callback.
        val source = new ScriptedPublisher(elements.map(OnNext(_)) :+ OnComplete, maxRequest = 2L)

        source.boundedStream[IO](2).compile.toList.timeout(3.seconds).unsafeToFuture().map { result =>
          result mustBe elements
          source.requests.isEmpty mustBe false
          val iterator = source.requests.iterator()
          while (iterator.hasNext) {
            val demand = iterator.next()
            demand must be > 0L
            demand must be <= 2L
            ()
          }
          succeed
        }
      }

      "request no more than the buffer while the downstream consumer is stopped" in {
        val source = new ScriptedPublisher(List.fill(20)(OnNext("a")), maxRequest = 2L)

        source.boundedStream[IO](2).take(1).compile.toList.timeout(2.seconds).unsafeToFuture().map { result =>
          result mustBe List("a")
          source.emitted.get() must be <= 3
          source.cancelCalls.get() mustBe 1
        }
      }

      "deliver a terminal error when the final requested element fills the buffer" in {
        val error  = new RuntimeException("full buffer failed")
        val source = new ScriptedPublisher(List(OnNext("a"), OnNext("b"), OnError(error)), maxRequest = 2L)

        source
          .boundedStream[IO](2)
          .map[Either[Throwable, String]](Right(_))
          .handleError(Left(_))
          .compile
          .toList
          .timeout(2.seconds)
          .unsafeToFuture()
          .map(_ mustBe List(Right("a"), Right("b"), Left(error)))
      }
    }
  }

  private def publisher(actions: List[Action[String]]): ScriptedPublisher =
    new ScriptedPublisher(actions)

  final private class ScriptedPublisher(actions: List[Action[String]], maxRequest: Long = Long.MaxValue) extends Publisher[String] {
    val cancelCalls = new AtomicInteger(0)
    val emitted     = new AtomicInteger(0)
    val requests    = new ConcurrentLinkedQueue[Long]()

    override def subscribe(subscriber: Subscriber[_ >: String]): Unit =
      subscriber.onSubscribe(new Subscription {
        private var remaining = actions
        private var demand    = 0L
        private var draining  = false
        private var stopped   = false

        override def request(n: Long): Unit = synchronized {
          requests.add(n)
          if (!stopped) {
            if (n <= 0L || n > maxRequest) {
              stopped = true
              subscriber.onError(new IllegalArgumentException(s"Unexpected demand: $n (maximum $maxRequest)"))
            } else {
              demand = if (Long.MaxValue - demand < n) Long.MaxValue else demand + n
              if (!draining) {
                draining = true
                try {
                  var continue = true
                  while (continue && !stopped)
                    remaining match {
                      case OnNext(element) :: rest if demand > 0L =>
                        remaining = rest
                        demand -= 1L
                        emitted.incrementAndGet()
                        subscriber.onNext(element)
                      case OnError(error) :: _ =>
                        stopped = true
                        subscriber.onError(error)
                      case OnComplete :: _ =>
                        stopped = true
                        subscriber.onComplete()
                      case _ => continue = false
                    }
                } finally draining = false
              }
            }
          }
        }

        override def cancel(): Unit = synchronized {
          cancelCalls.incrementAndGet()
          stopped = true
        }
      })
  }

  final private class ControlledPublisher(delayedSubscription: Boolean) extends Publisher[String] {
    val cancelCalls = new AtomicInteger(0)
    val requested   = new AtomicLong(0L)

    private val subscriber                 = new AtomicReference[Subscriber[_ >: String]]()
    private val subscribed                 = new CountDownLatch(1)
    private val requestReceived            = new CountDownLatch(1)
    private val subscriptionSent           = new AtomicBoolean(false)
    private val terminalSent               = new AtomicBoolean(false)
    private val subscription: Subscription = new Subscription {
      override def request(n: Long): Unit = {
        requested.addAndGet(n)
        requestReceived.countDown()
      }
      override def cancel(): Unit = {
        cancelCalls.incrementAndGet()
        ()
      }
    }

    override def subscribe(s: Subscriber[_ >: String]): Unit = {
      subscriber.set(s)
      subscribed.countDown()
      if (!delayedSubscription) deliverSubscription()
    }

    def awaitSubscription: IO[Unit] = await(subscribed)
    def awaitRequest: IO[Unit]      = await(requestReceived)

    private def await(latch: CountDownLatch): IO[Unit] =
      IO.blocking(latch.await(3L, TimeUnit.SECONDS)).flatMap { received =>
        IO.raiseUnless(received)(new RuntimeException("Publisher callback was not reached"))
      }

    def deliverSubscription(): Unit =
      if (subscriptionSent.compareAndSet(false, true)) subscriber.get().onSubscribe(subscription)

    def deliverAdditionalSubscription(subscription: Subscription): Unit = subscriber.get().onSubscribe(subscription)

    def emit(value: String): Unit = subscriber.get().onNext(value)

    def finish(): Unit =
      if (subscriber.get() != null) {
        deliverSubscription()
        if (cancelCalls.get() == 0 && terminalSent.compareAndSet(false, true)) subscriber.get().onComplete()
      }
  }
}
