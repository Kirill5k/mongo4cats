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
import com.mongodb.client.model.changestream.{
  ChangeStreamDocument,
  FullDocument => JFullDocument
}
import com.mongodb.reactivestreams.client.ChangeStreamPublisher
import fs2.Stream
import mongo4cats.helpers._
import mongo4cats.bson.Decoder
import mongo4cats.bson.syntax._
import mongo4cats.collection.queries.WatchCommand, WatchCommand._
import org.bson.{BsonDocument, BsonTimestamp}

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration

final case class WatchQueryBuilder(
    private val publisher: ChangeStreamPublisher[BsonDocument],
    private val commands: List[WatchCommand]
) {

  def batchSize(s: Int) =
    add(BatchSize(s))

  def collation(c: model.Collation) =
    add(Collation(c))

  def fullDocument(f: JFullDocument) =
    add(FullDocument(f))

  def maxAwaitTime(d: Duration) =
    add(MaxAwaitTime(d))

  def resumeAfter(rt: BsonDocument) =
    add(ResumeAfter(rt))

  def startAfter(sa: BsonDocument) =
    add(StartAfter(sa))

  def startAtOperationTime(saot: BsonTimestamp) =
    add(StartAtOperationTime(saot))

  def stream[F[_]: Async]: Stream[F, ChangeStreamDocument[BsonDocument]] =
    boundedStream[F](1)

  def boundedStream[F[_]: Async](c: Int): Stream[F, ChangeStreamDocument[BsonDocument]] =
    applyCommands.boundedStream[F](c)

  def updateStream[F[_]: Async, A: Decoder]: Stream[F, A] =
    stream.map(_.getFullDocument).evalMap(_.as[A].liftTo[F])

  private def applyCommands: ChangeStreamPublisher[BsonDocument] =
    commands.foldRight(publisher) { (command, acc) =>
      command match {
        case BatchSize(s) =>
          acc.batchSize(s)
        case Collation(c) =>
          acc.collation(c)
        case FullDocument(fd) =>
          acc.fullDocument(fd)
        case MaxAwaitTime(d) =>
          acc.maxAwaitTime(d.toNanos, TimeUnit.NANOSECONDS)
        case ResumeAfter(b) =>
          acc.resumeAfter(b)
        case StartAfter(b) =>
          acc.startAfter(b)
        case StartAtOperationTime(t) =>
          acc.startAtOperationTime(t)
      }
    }

  private def add(command: WatchCommand): WatchQueryBuilder =
    copy(commands = command :: commands)
}
