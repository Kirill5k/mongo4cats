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
import com.mongodb.reactivestreams.client.FindPublisher
import fs2.Stream
import mongo4cats.bson.Decoder
import mongo4cats.bson.syntax._
import mongo4cats.helpers._
import mongo4cats.collection.operations
import mongo4cats.collection.queries.FindCommand, FindCommand._
import org.bson.{BsonDocument, Document}
import org.bson.conversions.Bson

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration

final case class FindQueryBuilder(
    private val publisher: FindPublisher[BsonDocument],
    private val commands: List[FindCommand]
) {

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

  def sort(s: Bson): FindQueryBuilder =
    add(Sort(s))

  def sort(s: operations.Sort): FindQueryBuilder =
    sort(s.toBson)

  def sortBy(fieldNames: String*) =
    sort(operations.Sort.asc(fieldNames: _*))

  def sortByDesc(fieldNames: String*) =
    sort(operations.Sort.desc(fieldNames: _*))

  def filter(f: Bson): FindQueryBuilder =
    add(Filter(f))

  def filter(f: operations.Filter): FindQueryBuilder =
    filter(f.toBson)

  def projection(p: Bson): FindQueryBuilder =
    add(Projection(p))

  def projection(p: operations.Projection): FindQueryBuilder =
    projection(p.toBson)

  def skip(s: Int) =
    add(Skip(s))

  def limit(l: Int) =
    add(Limit(l))

  //

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

  private def applyCommands: FindPublisher[BsonDocument] =
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

  private def add(command: FindCommand): FindQueryBuilder =
    copy(commands = command :: commands)
}
