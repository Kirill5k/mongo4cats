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
import com.mongodb.ExplainVerbosity
import com.mongodb.client.model
import com.mongodb.reactivestreams.client.{AggregatePublisher, MongoCollection => JCollection}
import fs2.Stream
import mongo4cats.helpers._
import mongo4cats.bson.Decoder
import mongo4cats.bson.syntax._
import mongo4cats.client.ClientSession
import mongo4cats.collection.operations.{Aggregate, Index}
import mongo4cats.collection.queries.AggregateCommand, AggregateCommand._
import org.bson.{BsonDocument, BsonValue, Document}
import org.bson.conversions.Bson

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration
import scala.jdk.CollectionConverters._

final case class AggregateQueryBuilder(
    private val collection: JCollection[BsonDocument],
    private val pipeline: Seq[Bson],
    private val clientSession: Option[ClientSession],
    private val commands: List[AggregateCommand]
) {

  def allowDiskUse(allowDiskUse: Boolean) =
    add(AllowDiskUse(allowDiskUse))

  def maxTime(duration: Duration) =
    add(MaxTime(duration))

  def maxAwaitTime(duration: Duration) =
    add(MaxAwaitTime(duration))

  def bypassDocumentValidation(bypass: Boolean) =
    add(BypassDocumentValidation(bypass))

  def collation(collation: model.Collation) =
    add(Collation(collation))

  def comment(comment: String) =
    add(Comment(comment))

  def let(variables: Bson) =
    add(Let(variables))

  def hint(hint: Bson) =
    add(Hint(hint))

  def hint(index: Index) =
    add(Hint(index.toBson))

  def batchSize(batchSize: Int) =
    add(BatchSize(batchSize))

  //
  def session(cs: ClientSession) =
    copy(clientSession = Some(cs))

  def noSession =
    copy(clientSession = None)

  def pipeline(p: Seq[Bson]) =
    copy(pipeline = p)

  def pipeline(p: Aggregate) =
    copy(pipeline = p.toBsons)

  //
  def toCollection[F[_]: Async]: F[Unit] =
    applyCommands.toCollection.asyncVoid[F]

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

  def explain[F[_]: Async]: F[Document] =
    applyCommands.explain.asyncSingle[F]

  def explain[F[_]: Async](verbosity: ExplainVerbosity): F[Document] =
    applyCommands.explain(verbosity).asyncSingle[F]

  //
  private def applyCommands: AggregatePublisher[BsonValue] =
    commands.foldRight(publisher) { (command, acc) =>
      command match {
        case AllowDiskUse(b) =>
          acc.allowDiskUse(b)
        case MaxTime(d) =>
          acc.maxTime(d.toNanos, TimeUnit.NANOSECONDS)
        case MaxAwaitTime(d) =>
          acc.maxAwaitTime(d.toNanos, TimeUnit.NANOSECONDS)
        case BypassDocumentValidation(b) =>
          acc.bypassDocumentValidation(b)
        case Collation(c) =>
          acc.collation(c)
        case Comment(c) =>
          acc.comment(c)
        case Let(v) =>
          acc.let(v)
        case Hint(h) =>
          acc.hint(h)
        case BatchSize(i) =>
          acc.batchSize(i)
      }
    }

  private def publisher: AggregatePublisher[BsonValue] =
    clientSession match {
      case None     => collection.aggregate(pipeline.asJava, classOf[BsonValue])
      case Some(cs) => collection.aggregate(cs.session, pipeline.asJava, classOf[BsonValue])
    }

  private def add(command: AggregateCommand): AggregateQueryBuilder =
    copy(commands = command :: commands)
}
