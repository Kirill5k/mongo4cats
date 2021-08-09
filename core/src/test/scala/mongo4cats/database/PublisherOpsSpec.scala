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

import cats.implicits._
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import mongo4cats.database.helpers._
import org.reactivestreams.{Publisher, Subscriber}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import scala.concurrent.duration._

class PublisherOpsSpec extends AsyncWordSpec with Matchers {

  sealed trait Action[+T]              extends Serializable with Product
  case class OnNext[T](element: T)     extends Action[T]
  case class OnError(error: Throwable) extends Action[Nothing]
  case object OnComplete               extends Action[Nothing]

  "A PublisherOps" when {

    "stream" should {

      "convert elements into a stream" in {
        publisher(List(OnNext("a"), OnNext("b"), OnNext("c"), OnComplete))
          .stream[IO]
          .compile
          .toList
          .unsafeToFuture()
          .map(_ mustBe List("a", "b", "c"))
      }

      "process errors" in {
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

      "not terminate if there was no terminal signal" in {
        publisher(List(OnNext("a"), OnNext("b")))
          .stream[IO]
          .timeout(5.seconds)
          .compile
          .toList
          .attempt
          .unsafeToFuture()
          .map(_.leftMap(_.getMessage) mustBe Left(s"Timed out after ${5.seconds}"))
      }
    }

    def publisher(actions: List[Action[String]]): Publisher[String] =
      new Publisher[String] {
        override def subscribe(s: Subscriber[_ >: String]): Unit =
          actions.foreach {
            case OnNext(element) => s.onNext(element)
            case OnError(error)  => s.onError(error)
            case OnComplete      => s.onComplete()
          }
      }
  }
}
