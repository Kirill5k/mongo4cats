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
import org.reactivestreams.Publisher

object helpers {

  val DefaultStreamChunkSize: Int = 4096

  implicit final class PublisherOps[T](private val publisher: Publisher[T]) extends AnyVal {
    def asyncSingle[F[_]: Async]: F[T] =
      boundedStream(1).take(1).compile.lastOrError

    def asyncOption[F[_]: Async]: F[Option[T]] =
      boundedStream(1).take(1).compile.last

    def asyncVoid[F[_]: Async]: F[Unit] =
      boundedStream(1).compile.drain

    def stream[F[_]: Async]: Stream[F, T] =
      reactivestreams.fromPublisher(publisher, DefaultStreamChunkSize)

    def boundedStream[F[_]: Async](chunkSize: Int): Stream[F, T] =
      reactivestreams.fromPublisher(publisher, chunkSize)
  }
}
