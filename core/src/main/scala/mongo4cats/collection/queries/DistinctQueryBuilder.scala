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
import com.mongodb.client.model
import com.mongodb.reactivestreams.client.{DistinctPublisher, MongoCollection => JCollection}
import fs2.Stream
import mongo4cats.helpers._
import mongo4cats.bson.BsonDecoder
import mongo4cats.bson.syntax._
import mongo4cats.client.ClientSession
import mongo4cats.collection.operations.{Filter => OFilter}
import mongo4cats.collection.queries.DistinctCommand._
import org.bson.{BsonDocument, BsonValue}
import org.bson.conversions.Bson

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration

trait DistinctQueryBuilder[F[_]] {
  def maxTime(duration: Duration): DistinctQueryBuilder[F]
  def filter(filter: OFilter): DistinctQueryBuilder[F]
  def batchSize(size: Int): DistinctQueryBuilder[F]
  def collation(collation: model.Collation): DistinctQueryBuilder[F]

  //
  def session(cs: ClientSession[F]): DistinctQueryBuilder[F]

  def noSession: DistinctQueryBuilder[F]

  //
  def first[A: BsonDecoder]: F[Option[A]]
  def stream[A: BsonDecoder]: Stream[F, A]
  def boundedStream[A: BsonDecoder](c: Int): Stream[F, A]

  //
  def mapK[G[_]](f: F ~> G): DistinctQueryBuilder[G]
}

object DistinctQueryBuilder {
  def apply[F[_]: Async](
      fieldName: String,
      collection: JCollection[BsonDocument]
  ): DistinctQueryBuilder[F] =
    TransformedDistinctQueryBuilder[F, F](
      fieldName,
      collection,
      None,
      List.empty,
      FunctionK.id
    )

  final private case class TransformedDistinctQueryBuilder[F[_]: Async, G[_]](
      fieldName: String,
      collection: JCollection[BsonDocument],
      clientSession: Option[ClientSession[G]],
      commands: List[DistinctCommand],
      transform: F ~> G
  ) extends DistinctQueryBuilder[G] {

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

    //
    def session(cs: ClientSession[G]) =
      copy(clientSession = Some(cs))

    def noSession =
      copy(clientSession = None)

    //

    def first[A: BsonDecoder]: G[Option[A]] = transform {
      applyCommands.first
        .asyncOption[F]
        .flatMap(_.traverse { bson =>
          bson.as[A].liftTo[F]
        })
    }

    def stream[A: BsonDecoder] =
      applyCommands.stream[F].evalMap(_.as[A].liftTo[F]).translate(transform)

    def boundedStream[A: BsonDecoder](c: Int) =
      applyCommands.boundedStream[F](c).evalMap(_.as[A].liftTo[F]).translate(transform)

    //
    def mapK[H[_]](f: G ~> H) =
      copy(transform = transform andThen f, clientSession = clientSession.map(_.mapK(f)))

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

    private def add(command: DistinctCommand): TransformedDistinctQueryBuilder[F, G] =
      copy(commands = command :: commands)

    private def publisher: DistinctPublisher[BsonValue] =
      clientSession match {
        case None     => collection.distinct(fieldName, classOf[BsonValue])
        case Some(cs) => collection.distinct(cs.session, fieldName, classOf[BsonValue])
      }

  }
}
