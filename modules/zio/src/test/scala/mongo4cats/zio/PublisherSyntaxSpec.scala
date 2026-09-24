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

import mongo4cats.zio.syntax._
import org.reactivestreams.{Publisher, Subscriber, Subscription}
import zio.{durationInt, Scope, Task, ZIO}
import zio.test._
import zio.test.Assertion._

import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.{AtomicInteger, AtomicLong}

object PublisherSyntaxSpec extends ZIOSpecDefault {

  private val adapters: List[(String, Publisher[Int] => Task[Any], Long)] = List(
    ("asyncVoid", _.asyncVoid, 1L),
    ("asyncSingle", _.asyncSingle, 1L),
    ("asyncIterable", _.asyncIterable, Long.MaxValue),
    ("asyncIterableF", _.asyncIterableF(_.toString), Long.MaxValue)
  )

  override def spec: Spec[TestEnvironment with Scope, Any] = suite("Publisher syntax")(
    suite("cancellation")(
      adapters.map { case (name, run, demand) =>
        suite(name)(
          test("cancels its subscription when interrupted") {
            for {
              publisher <- ZIO.succeed(new ControlledPublisher(immediate = true))
              fiber     <- run(publisher).fork
              _         <- ZIO.fromCompletionStage(publisher.subscribed)
              exit      <- fiber.interrupt
              _         <- fiber.interrupt
            } yield assertTrue(exit.isInterrupted, publisher.cancelCount.get() == 1, publisher.requested.get() == demand)
          },
          test("cancels a subscription arriving after interruption without requesting elements") {
            for {
              publisher  <- ZIO.succeed(new ControlledPublisher(immediate = false))
              fiber      <- run(publisher).fork
              subscriber <- ZIO.fromCompletionStage(publisher.subscribed)
              exit       <- fiber.interrupt
              _          <- ZIO.succeed(subscriber.onSubscribe(publisher.subscription))
            } yield assertTrue(exit.isInterrupted, publisher.cancelCount.get() == 1, publisher.requested.get() == 0L)
          },
          test("propagates publisher errors") {
            val error = new RuntimeException("publisher failed")
            assertZIO(run(publisher(Nil, Some(error))).exit)(fails(equalTo(error)))
          },
          test("cancels its subscription when registration throws") {
            val error = new RuntimeException("registration failed")
            for {
              publisher <- ZIO.succeed(new ControlledPublisher(immediate = true, registrationError = Some(error)))
              exit      <- run(publisher).exit
            } yield assert(exit)(dies(equalTo(error))) && assertTrue(publisher.cancelCount.get() == 1)
          }
        )
      }
    ),
    test("completes a void result") {
      assertZIO(publisher(List(1)).asyncVoid)(isUnit)
    },
    test("returns a scalar result") {
      assertZIO(publisher(List(1)).asyncSingle)(isSome(equalTo(1)))
    },
    test("returns an empty scalar result") {
      assertZIO(publisher(Nil).asyncSingle)(isNone)
    },
    test("collects iterable results in order") {
      assertZIO(publisher(List(1, 2, 3)).asyncIterable.map(_.toList))(equalTo(List(1, 2, 3)))
    },
    test("maps iterable results in order") {
      assertZIO(publisher(List(1, 2, 3)).asyncIterableF(_.toString).map(_.toList))(equalTo(List("1", "2", "3")))
    },
    test("allocates a new result buffer for each execution") {
      val task = publisher(List(1, 2, 3)).asyncIterable.map(_.toList)
      assertZIO(task *> task)(equalTo(List(1, 2, 3)))
    }
  ) @@ TestAspect.timeout(10.seconds)

  final private class ControlledPublisher(immediate: Boolean, registrationError: Option[Throwable] = None) extends Publisher[Int] {
    val subscribed                = new CompletableFuture[Subscriber[_ >: Int]]()
    val cancelCount               = new AtomicInteger()
    val requested                 = new AtomicLong()
    val subscription: Subscription = new Subscription {
      override def request(n: Long): Unit = {
        requested.addAndGet(n)
        ()
      }

      override def cancel(): Unit = {
        cancelCount.incrementAndGet()
        ()
      }
    }

    override def subscribe(subscriber: Subscriber[_ >: Int]): Unit = {
      if (immediate) subscriber.onSubscribe(subscription)
      subscribed.complete(subscriber)
      registrationError.foreach(throw _)
    }
  }

  private def publisher(values: List[Int], error: Option[Throwable] = None): Publisher[Int] =
    new Publisher[Int] {
      override def subscribe(subscriber: Subscriber[_ >: Int]): Unit =
        subscriber.onSubscribe(new Subscription {
          private var remaining = values
          private var stopped   = false

          override def request(n: Long): Unit = {
            var outstanding = n
            while (outstanding > 0L && remaining.nonEmpty && !stopped) {
              val value = remaining.head
              remaining = remaining.tail
              outstanding -= 1L
              subscriber.onNext(value)
            }
            if (remaining.isEmpty && !stopped) {
              stopped = true
              error match {
                case Some(cause) => subscriber.onError(cause)
                case None        => subscriber.onComplete()
              }
            }
          }

          override def cancel(): Unit = stopped = true
        })
    }
}
