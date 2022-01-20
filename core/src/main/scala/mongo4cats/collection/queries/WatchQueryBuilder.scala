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

import cats.arrow.FunctionK
import cats.~>
import cats.effect.Async
import cats.implicits._
import com.mongodb.client.model
import com.mongodb.client.model.changestream.{
  ChangeStreamDocument,
  FullDocument => JFullDocument
}
import com.mongodb.reactivestreams.client.{
  ChangeStreamPublisher,
  MongoCollection => JCollection
}
import fs2.Stream
import mongo4cats.helpers._
import mongo4cats.bson.BsonDecoder
import mongo4cats.bson.syntax._
import mongo4cats.client.ClientSession
import mongo4cats.collection.operations.Aggregate
import mongo4cats.collection.queries.WatchCommand._
import org.bson.{BsonDocument, BsonTimestamp, BsonValue}
import org.bson.conversions.Bson

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration
import scala.jdk.CollectionConverters._

trait WatchQueryBuilder[F[_]] {
  def batchSize(s: Int): WatchQueryBuilder[F]
  def collation(c: model.Collation): WatchQueryBuilder[F]
  def fullDocument(f: JFullDocument): WatchQueryBuilder[F]
  def maxAwaitTime(d: Duration): WatchQueryBuilder[F]
  def resumeAfter(rt: BsonDocument): WatchQueryBuilder[F]
  def startAfter(sa: BsonDocument): WatchQueryBuilder[F]
  def startAtOperationTime(saot: BsonTimestamp): WatchQueryBuilder[F]

  //
  def session(cs: ClientSession[F]): WatchQueryBuilder[F]
  def noSession: WatchQueryBuilder[F]
  def pipeline(p: Aggregate): WatchQueryBuilder[F]

  //
  def stream: Stream[F, ChangeStreamDocument[BsonValue]]
  def boundedStream(c: Int): Stream[F, ChangeStreamDocument[BsonValue]]
  def updateStream[A: BsonDecoder]: Stream[F, A]

  def mapK[G[_]](f: F ~> G): WatchQueryBuilder[G]
}

object WatchQueryBuilder {
  def apply[F[_]: Async](collection: JCollection[BsonDocument]): WatchQueryBuilder[F] =
    TransformedWatchQueryBuilder[F, F](
      collection,
      List.empty,
      None,
      List.empty,
      FunctionK.id
    )

  final private case class TransformedWatchQueryBuilder[F[_]: Async, G[_]](
      collection: JCollection[BsonDocument],
      pipeline: Seq[Bson],
      clientSession: Option[ClientSession[G]],
      commands: List[WatchCommand],
      transform: F ~> G
  ) extends WatchQueryBuilder[G] {

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

    //
    def session(cs: ClientSession[G]) =
      copy(clientSession = Some(cs))

    def noSession =
      copy(clientSession = None)

    def pipeline(p: Seq[Bson]) =
      copy(pipeline = p)

    def pipeline(p: Aggregate) =
      copy(pipeline = p.toBsons)

    //
    def stream =
      boundedStream(1)

    def boundedStream(c: Int) =
      boundedStreamF(c).translate(transform)

    def updateStream[A: BsonDecoder] =
      boundedStreamF(1).map(_.getFullDocument).evalMap(_.as[A].liftTo[F]).translate(transform)

    //
    def mapK[H[_]](f: G ~> H) =
      copy(transform = transform andThen f, clientSession = clientSession.map(_.mapK(f)))

    //
    private def boundedStreamF(c: Int): Stream[F, ChangeStreamDocument[BsonValue]] =
      applyCommands.boundedStream(c)

    private def applyCommands: ChangeStreamPublisher[BsonValue] =
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

    private def add(command: WatchCommand): TransformedWatchQueryBuilder[F, G] =
      copy(commands = command :: commands)

    private def publisher: ChangeStreamPublisher[BsonValue] =
      clientSession match {
        case None     => collection.watch(pipeline.asJava, classOf[BsonValue])
        case Some(cs) => collection.watch(cs.session, pipeline.asJava, classOf[BsonValue])
      }

  }
}
