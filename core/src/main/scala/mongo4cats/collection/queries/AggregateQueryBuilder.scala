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

import cats.~>
import cats.arrow.FunctionK
import cats.effect.Async
import cats.implicits._
import com.mongodb.ExplainVerbosity
import com.mongodb.client.model
import com.mongodb.reactivestreams.client.{AggregatePublisher, MongoCollection => JCollection}
import fs2.Stream
import mongo4cats.helpers._
import mongo4cats.bson.BsonDecoder
import mongo4cats.bson.syntax._
import mongo4cats.client.ClientSession
import mongo4cats.collection.operations.{Aggregate, Index}
import mongo4cats.collection.queries.AggregateCommand._
import org.bson.{BsonDocument, BsonValue}
import org.bson.conversions.Bson

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration
import scala.jdk.CollectionConverters._

trait AggregateQueryBuilder[F[_]] {
  def allowDiskUse(allowDiskUse: Boolean): AggregateQueryBuilder[F]
  def maxTime(duration: Duration): AggregateQueryBuilder[F]
  def maxAwaitTime(duration: Duration): AggregateQueryBuilder[F]
  def bypassDocumentValidation(bypass: Boolean): AggregateQueryBuilder[F]
  def collation(collation: model.Collation): AggregateQueryBuilder[F]
  def comment(comment: String): AggregateQueryBuilder[F]
  def let(variables: Bson): AggregateQueryBuilder[F]
  def hint(hint: Bson): AggregateQueryBuilder[F]
  def hint(index: Index): AggregateQueryBuilder[F]
  def batchSize(batchSize: Int): AggregateQueryBuilder[F]

  //
  def session(cs: ClientSession[F]): AggregateQueryBuilder[F]
  def noSession: AggregateQueryBuilder[F]

  def pipeline(p: Aggregate): AggregateQueryBuilder[F]

  //
  def toCollection: F[Unit]
  def first[A: BsonDecoder]: F[Option[A]]
  def stream[A: BsonDecoder]: Stream[F, A]
  def boundedStream[A: BsonDecoder](c: Int): Stream[F, A]
  def explain: F[BsonDocument]
  def explain(verbosity: ExplainVerbosity): F[BsonDocument]

  //
  def mapK[G[_]](nat: F ~> G): AggregateQueryBuilder[G]
}

object AggregateQueryBuilder {
  def apply[F[_]: Async](collection: JCollection[BsonDocument]): AggregateQueryBuilder[F] =
    TransformedAggregateQueryBuilder[F, F](
      collection,
      List.empty,
      None,
      List.empty,
      FunctionK.id
    )

  final private case class TransformedAggregateQueryBuilder[F[_]: Async, G[_]](
      collection: JCollection[BsonDocument],
      pipeline: Seq[Bson],
      clientSession: Option[ClientSession[G]],
      commands: List[AggregateCommand],
      transform: F ~> G
  ) extends AggregateQueryBuilder[G] {

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
    def session(cs: ClientSession[G]) =
      copy(clientSession = Some(cs))

    def noSession =
      copy(clientSession = None)

    def pipeline(p: Aggregate) =
      copy(pipeline = p.toBsons)

    //
    def toCollection: G[Unit] =
      transform(applyCommands.toCollection.asyncVoid[F])

    def first[A: BsonDecoder]: G[Option[A]] = transform {
      applyCommands.first
        .asyncOption[F]
        .flatMap(_.traverse { bson =>
          bson.as[A].liftTo[F]
        })
    }

    def stream[A: BsonDecoder]: Stream[G, A] =
      applyCommands.stream[F].evalMap(_.as[A].liftTo[F]).translate(transform)

    def boundedStream[A: BsonDecoder](c: Int): Stream[G, A] =
      applyCommands.boundedStream[F](c).evalMap(_.as[A].liftTo[F]).translate(transform)

    def explain: G[BsonDocument] = transform {
      applyCommands.explain(classOf[BsonDocument]).asyncSingle[F]
    }

    def explain(verbosity: ExplainVerbosity): G[BsonDocument] = transform {
      applyCommands.explain(classOf[BsonDocument], verbosity).asyncSingle[F]
    }
    //
    def mapK[H[_]](f: G ~> H): AggregateQueryBuilder[H] =
      copy(transform = transform andThen f, clientSession = clientSession.map(_.mapK(f)))

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

    private def add(command: AggregateCommand): TransformedAggregateQueryBuilder[F, G] =
      copy(commands = command :: commands)
  }
}
