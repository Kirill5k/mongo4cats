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
import com.mongodb.ExplainVerbosity
import com.mongodb.client.model
import com.mongodb.reactivestreams.client.{FindPublisher, MongoCollection => JCollection}
import fs2.Stream
import mongo4cats.bson.Decoder
import mongo4cats.bson.syntax._
import mongo4cats.helpers._
import mongo4cats.client.ClientSession
import mongo4cats.collection.operations
import mongo4cats.collection.queries.FindCommand, FindCommand._
import org.bson.{BsonDocument, BsonValue}
import org.bson.conversions.Bson

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration

trait FindQueryBuilder[F[_]] {
  def maxTime(duration: Duration): FindQueryBuilder[F]
  def maxAwaitTime(duration: Duration): FindQueryBuilder[F]
  def collation(collation: model.Collation): FindQueryBuilder[F]
  def partial(p: Boolean): FindQueryBuilder[F]
  def comment(c: String): FindQueryBuilder[F]
  def returnKey(rk: Boolean): FindQueryBuilder[F]
  def showRecordId(s: Boolean): FindQueryBuilder[F]
  def hint(index: String): FindQueryBuilder[F]
  def hint(h: Bson): FindQueryBuilder[F]
  def max(m: Bson): FindQueryBuilder[F]
  def min(m: Bson): FindQueryBuilder[F]
  def sort(s: operations.Sort): FindQueryBuilder[F]
  def sortBy(fieldNames: String*): FindQueryBuilder[F]
  def sortByDesc(fieldNames: String*): FindQueryBuilder[F]
  def filter(f: operations.Filter): FindQueryBuilder[F]
  def projection(p: operations.Projection): FindQueryBuilder[F]
  def skip(s: Int): FindQueryBuilder[F]
  def limit(l: Int): FindQueryBuilder[F]

  //
  def session(s: ClientSession[F]): FindQueryBuilder[F]

  def noSession: FindQueryBuilder[F]

  //
  def first[A: Decoder]: F[Option[A]]

  def stream[A: Decoder]: Stream[F, A]

  def boundedStream[A: Decoder](c: Int): Stream[F, A]

  def explain: F[BsonDocument]

  def explain(verbosity: ExplainVerbosity): F[BsonDocument]

  //
  def mapK[G[_]](f: F ~> G): FindQueryBuilder[G]
}
object FindQueryBuilder {
  def apply[F[_]: Async](collection: JCollection[BsonDocument]): FindQueryBuilder[F] =
    new TransformedFindQueryBuilder[F, F](collection, None, List.empty, FunctionK.id)

  final private case class TransformedFindQueryBuilder[F[_]: Async, G[_]](
      collection: JCollection[BsonDocument],
      clientSession: Option[ClientSession[G]],
      commands: List[FindCommand],
      transform: F ~> G
  ) extends FindQueryBuilder[G] {

    def maxTime(duration: Duration) =
      add(MaxTime(duration))

    def maxAwaitTime(duration: Duration) =
      add(MaxAwaitTime(duration))

    def collation(collation: model.Collation) =
      add(Collation(collation))

    def partial(p: Boolean) =
      add(Partial(p))

    def comment(c: String) =
      add(Comment(c))

    def returnKey(rk: Boolean) =
      add(ReturnKey(rk))

    def showRecordId(s: Boolean) =
      add(ShowRecordId(s))

    def hint(index: String) =
      add(HintString(index))

    def hint(h: Bson) =
      add(Hint(h))

    def max(m: Bson) =
      add(Max(m))

    def min(m: Bson) =
      add(Min(m))

    def sort(s: operations.Sort) =
      add(Sort(s.toBson))

    def sortBy(fieldNames: String*) =
      sort(operations.Sort.asc(fieldNames: _*))

    def sortByDesc(fieldNames: String*) =
      sort(operations.Sort.desc(fieldNames: _*))

    def filter(f: operations.Filter) =
      add(Filter(f.toBson))

    def projection(p: operations.Projection) =
      add(Projection(p.toBson))

    def skip(s: Int) =
      add(Skip(s))

    def limit(l: Int) =
      add(Limit(l))

    //
    def session(s: ClientSession[G]) =
      copy(clientSession = Some(s))

    def noSession =
      copy(clientSession = None)
    //

    def first[A: Decoder] = transform {
      applyCommands.first
        .asyncOption[F]
        .flatMap(_.traverse { bson =>
          bson.as[A].liftTo[F]
        })
    }

    def stream[A: Decoder] =
      applyCommands.stream[F].evalMap(_.as[A].liftTo[F]).translate(transform)

    def boundedStream[A: Decoder](c: Int) =
      applyCommands.boundedStream[F](c).evalMap(_.as[A].liftTo[F]).translate(transform)

    def explain = transform {
      applyCommands.explain(classOf[BsonDocument]).asyncSingle[F]
    }

    def explain(verbosity: ExplainVerbosity) = transform {
      applyCommands.explain(classOf[BsonDocument], verbosity).asyncSingle[F]
    }

    //
    def mapK[H[_]](f: G ~> H) =
      copy(transform = transform andThen f, clientSession = clientSession.map(_.mapK(f)))

    //

    private def applyCommands: FindPublisher[BsonValue] =
      commands.foldRight(publisher) { (command, acc) =>
        command match {
          case ShowRecordId(s) =>
            acc.showRecordId(s)
          case ReturnKey(r) =>
            acc.returnKey(r)
          case Comment(c) =>
            acc.comment(c)
          case Collation(c) =>
            acc.collation(c)
          case Partial(b) =>
            acc.partial(b)
          case MaxTime(d) =>
            acc.maxTime(d.toNanos, TimeUnit.NANOSECONDS)
          case MaxAwaitTime(d) =>
            acc.maxTime(d.toNanos, TimeUnit.NANOSECONDS)
          case HintString(s) =>
            acc.hintString(s)
          case Hint(h) =>
            acc.hint(h)
          case Max(m) =>
            acc.max(m)
          case Min(m) =>
            acc.min(m)
          case Skip(s) =>
            acc.skip(s)
          case Limit(l) =>
            acc.limit(l)
          case Filter(f) =>
            acc.filter(f)
          case Projection(p) =>
            acc.projection(p)
          case Sort(s) =>
            acc.sort(s)
        }
      }

    private def add(command: FindCommand): TransformedFindQueryBuilder[F, G] =
      copy(commands = command :: commands)

    private def publisher: FindPublisher[BsonValue] =
      clientSession match {
        case None     => collection.find(classOf[BsonValue])
        case Some(cs) => collection.find(cs.session, classOf[BsonValue])
      }
  }
}
