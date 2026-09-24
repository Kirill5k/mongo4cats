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

package mongo4cats.collection

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.parallel._
import cats.syntax.traverse._
import com.mongodb.ExplainVerbosity
import mongo4cats.queries.MutableQueryPublisher
import org.bson.BsonDocument
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AsyncWordSpec

class QueriesSpec extends AsyncWordSpec with Matchers {

  private val terminals: List[(String, MutableQueryPublisher => IO[Unit])] = List(
    "find.first"                   -> (p => Queries.find[IO, String](p.find()).limit(2).first.void),
    "find.all"                     -> (p => Queries.find[IO, String](p.find()).limit(2).all.void),
    "find.stream"                  -> (p => Queries.find[IO, String](p.find()).limit(2).stream.compile.drain),
    "find.boundedStream"           -> (p => Queries.find[IO, String](p.find()).limit(2).boundedStream(1).compile.drain),
    "find.explain"                 -> (p => Queries.find[IO, String](p.find()).limit(2).explain.void),
    "find.explain(verbosity)"      -> (p => Queries.find[IO, String](p.find()).limit(2).explain(ExplainVerbosity.EXECUTION_STATS).void),
    "distinct.first"               -> (p => Queries.distinct[IO, String](p.distinct()).batchSize(2).first.void),
    "distinct.all"                 -> (p => Queries.distinct[IO, String](p.distinct()).batchSize(2).all.void),
    "distinct.stream"              -> (p => Queries.distinct[IO, String](p.distinct()).batchSize(2).stream.compile.drain),
    "distinct.boundedStream"       -> (p => Queries.distinct[IO, String](p.distinct()).batchSize(2).boundedStream(1).compile.drain),
    "aggregate.first"              -> (p => Queries.aggregate[IO, String](p.aggregate()).batchSize(2).first.void),
    "aggregate.all"                -> (p => Queries.aggregate[IO, String](p.aggregate()).batchSize(2).all.void),
    "aggregate.stream"             -> (p => Queries.aggregate[IO, String](p.aggregate()).batchSize(2).stream.compile.drain),
    "aggregate.boundedStream"      -> (p => Queries.aggregate[IO, String](p.aggregate()).batchSize(2).boundedStream(1).compile.drain),
    "aggregate.explain"            -> (p => Queries.aggregate[IO, String](p.aggregate()).batchSize(2).explain.void),
    "aggregate.explain(verbosity)" -> (p =>
      Queries.aggregate[IO, String](p.aggregate()).batchSize(2).explain(ExplainVerbosity.EXECUTION_STATS).void
    ),
    "aggregate.toCollection" -> (p => Queries.aggregate[IO, String](p.aggregate()).batchSize(2).toCollection),
    "watch.stream"           -> (p => Queries.watch[IO, String](p.watch()).batchSize(2).stream.compile.drain),
    "watch.boundedStream"    -> (p => Queries.watch[IO, String](p.watch()).batchSize(2).boundedStream(1).compile.drain)
  )

  "Query definitions" should {
    "keep a derived limit from contaminating an already assembled base effect" in {
      val source  = new MutableQueryPublisher
      val base    = Queries.find[IO, String](source.find())
      val limited = base.limit(1).all
      val all     = base.all

      (for {
        first  <- limited
        second <- all
      } yield {
        first.toList mustBe List("one")
        second.toList mustBe List("one", "two", "three")
        source.snapshots.map(_.options) mustBe List(Map("limit" -> 1), Map.empty)
      }).unsafeToFuture()
    }

    "isolate sibling filters, projections and limits in either execution order" in {
      val source      = new MutableQueryPublisher
      val base        = Queries.find[IO, String](source.find())
      val firstFilter = BsonDocument.parse("{\"group\": \"first\"}")
      val nextFilter  = BsonDocument.parse("{\"group\": \"next\"}")
      val firstFields = BsonDocument.parse("{\"first\": 1}")
      val nextFields  = BsonDocument.parse("{\"next\": 1}")
      val first       = base.filter(firstFilter).projection(firstFields).limit(1).all
      val second      = base.filter(nextFilter).projection(nextFields).limit(2).all
      val all         = base.all

      List(second, first, all, first, second, all).sequence.unsafeToFuture().map { results =>
        results.map(_.size) mustBe List(2, 1, 3, 1, 2, 3)
        source.snapshots.map(_.options) mustBe List(
          Map[String, Any]("filter" -> nextFilter, "projection"  -> nextFields, "limit"  -> 2),
          Map[String, Any]("filter" -> firstFilter, "projection" -> firstFields, "limit" -> 1),
          Map.empty,
          Map[String, Any]("filter" -> firstFilter, "projection" -> firstFields, "limit" -> 1),
          Map[String, Any]("filter" -> nextFilter, "projection"  -> nextFields, "limit"  -> 2),
          Map.empty
        )
      }
    }

    "apply commands in definition order independently for concurrent branches" in {
      val source = new MutableQueryPublisher
      val base   = Queries.find[IO, String](source.find()).limit(3)
      val first  = base.limit(1).all
      val second = base.limit(1).limit(2).all
      val all    = base.all

      List.fill(8)(List(first, second, all)).flatten.parSequence.unsafeToFuture().map { results =>
        results.map(_.size) mustBe List.fill(8)(List(1, 2, 3)).flatten
        source.snapshots.map(_.id).distinct.size mustBe 24
      }
    }

    "report publisher factory failures when the effect runs" in {
      val failure = new IllegalStateException("publisher factory failed")
      val query   = Queries.find[IO, String](throw failure)
      val result  = query.limit(1).all

      result.attempt.unsafeToFuture().map(_ mustBe Left(failure))
    }
  }

  terminals.foreach { case (name, build) =>
    name should {
      "create and configure a fresh publisher for every repeated or concurrent execution" in {
        val source = new MutableQueryPublisher
        val effect = build(source)

        source.created.get() mustBe 0
        source.configured.get() mustBe 0

        (for {
          _ <- effect
          _ <- effect
          _ <- List.fill(8)(effect).parSequence
        } yield {
          source.created.get() mustBe 10
          source.configured.get() mustBe 10
          source.snapshots.size mustBe 10
          source.snapshots.map(_.id).distinct.size mustBe 10
          source.snapshots.map(_.options).distinct.size mustBe 1
        }).unsafeToFuture()
      }
    }
  }
}
