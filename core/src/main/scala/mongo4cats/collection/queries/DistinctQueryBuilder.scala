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

package mongo4cats.collection.queries

import cats.effect.Async
import cats.implicits._
import com.mongodb.client.model
import com.mongodb.reactivestreams.client.DistinctPublisher
import fs2.Stream
import mongo4cats.helpers._
import mongo4cats.bson.Decoder
import mongo4cats.bson.syntax._
import mongo4cats.collection.operations.{Filter => OFilter}
import mongo4cats.collection.queries.DistinctCommand, DistinctCommand._
import org.bson.BsonValue
import org.bson.conversions.Bson

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration

final case class DistinctQueryBuilder(
    private val publisher: DistinctPublisher[BsonValue],
    private val commands: List[DistinctCommand]
) {

  def maxTime(duration: Duration) =
    add(MaxTime(duration))

  def filter(filter: Bson) =
    add(Filter(filter))

  def filter(filter: OFilter) =
    add(Filter(filter.toBson))

  def batchSize(size: Int) =
    add(BatchSize(size))

  def collation(collation: model.Collation) =
    add(Collation(collation))

  def first[F[_]: Async, A: Decoder]: F[Option[A]] =
    applyCommands.first
      .asyncOption[F]
      .flatMap(_.traverse { bson =>
        bson.as[A].liftTo[F]
      })

  def stream[F[_]: Async, A: Decoder]: Stream[F, A] =
    applyCommands.stream[F].evalMap(_.as[A].liftTo[F])

  def boundedStream[F[_]: Async, A: Decoder](c: Int) =
    applyCommands.boundedStream[F](c).evalMap(_.as[A].liftTo[F])

  private def applyCommands =
    commands.foldRight(publisher) { (command, acc) =>
      command match {
        case MaxTime(d) =>
          acc.maxTime(d.toNanos, TimeUnit.NANOSECONDS)
        case Filter(f) =>
          acc.filter(f)
        case BatchSize(s) =>
          acc.batchSize(s)
        case Collation(c) =>
          acc.collation(c)
      }
    }

  private def add(command: DistinctCommand): DistinctQueryBuilder =
    copy(commands = command :: commands)
}
