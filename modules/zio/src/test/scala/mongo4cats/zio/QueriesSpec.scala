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

package mongo4cats.zio

import com.mongodb.ExplainVerbosity
import mongo4cats.queries.MutableQueryPublisher
import org.bson.BsonDocument
import zio.{durationInt, Scope, Task, ZIO}
import zio.test._
import zio.test.Assertion._

object QueriesSpec extends ZIOSpecDefault {

  private val terminals: List[(String, MutableQueryPublisher => Task[Any])] = List(
    ("find.first", driver => Queries.find(driver.find()).limit(2).first),
    ("find.all", driver => Queries.find(driver.find()).limit(2).all),
    ("find.stream", driver => Queries.find(driver.find()).limit(2).stream.runCollect),
    ("find.boundedStream", driver => Queries.find(driver.find()).limit(2).boundedStream(2).runCollect),
    ("find.explain", driver => Queries.find(driver.find()).limit(2).explain),
    ("find.explain(verbosity)", driver => Queries.find(driver.find()).limit(2).explain(ExplainVerbosity.EXECUTION_STATS)),
    ("distinct.first", driver => Queries.distinct(driver.distinct()).batchSize(2).first),
    ("distinct.all", driver => Queries.distinct(driver.distinct()).batchSize(2).all),
    ("distinct.stream", driver => Queries.distinct(driver.distinct()).batchSize(2).stream.runCollect),
    ("distinct.boundedStream", driver => Queries.distinct(driver.distinct()).batchSize(2).boundedStream(2).runCollect),
    ("aggregate.first", driver => Queries.aggregate(driver.aggregate()).batchSize(2).first),
    ("aggregate.all", driver => Queries.aggregate(driver.aggregate()).batchSize(2).all),
    ("aggregate.stream", driver => Queries.aggregate(driver.aggregate()).batchSize(2).stream.runCollect),
    ("aggregate.boundedStream", driver => Queries.aggregate(driver.aggregate()).batchSize(2).boundedStream(2).runCollect),
    ("aggregate.explain", driver => Queries.aggregate(driver.aggregate()).batchSize(2).explain),
    (
      "aggregate.explain(verbosity)",
      driver => Queries.aggregate(driver.aggregate()).batchSize(2).explain(ExplainVerbosity.EXECUTION_STATS)
    ),
    ("aggregate.toCollection", driver => Queries.aggregate(driver.aggregate()).batchSize(2).toCollection),
    ("watch.stream", driver => Queries.watch(driver.watch()).batchSize(2).stream.runCollect),
    ("watch.boundedStream", driver => Queries.watch(driver.watch()).batchSize(2).boundedStream(2).runCollect)
  )

  override def spec: Spec[TestEnvironment with Scope, Any] = suite("Query publishers")(
    test("keeps assembled find effects and their base independent") {
      val driver                 = new MutableQueryPublisher
      val base                   = Queries.find(driver.find())
      val filter                 = BsonDocument.parse("{\"name\": \"one\"}")
      val projection             = BsonDocument.parse("{\"name\": 1}")
      val siblingFilter          = BsonDocument.parse("{\"name\": \"two\"}")
      val limited                = base.filter(filter).projection(projection).limit(2).limit(1).all
      val original               = base.all
      val sibling                = base.filter(siblingFilter).limit(2).all
      val createdBeforeExecution = driver.created.get()

      for {
        limitedResult  <- limited
        originalResult <- original
        siblingResult  <- sibling
        originalAgain  <- original
      } yield assertTrue(
        createdBeforeExecution == 0,
        limitedResult.toList == List("one"),
        originalResult.toList == List("one", "two", "three"),
        siblingResult.toList == List("one", "two"),
        originalAgain.toList == List("one", "two", "three"),
        driver.snapshots.map(_.options) == List(
          Map[String, Any]("filter" -> filter, "projection" -> projection, "limit" -> 1),
          Map.empty[String, Any],
          Map[String, Any]("filter" -> siblingFilter, "limit" -> 2),
          Map.empty[String, Any]
        )
      )
    },
    test("keeps distinct filters isolated from their base and siblings") {
      val driver        = new MutableQueryPublisher
      val base          = Queries.distinct(driver.distinct())
      val filter        = BsonDocument.parse("{\"name\": \"one\"}")
      val siblingFilter = BsonDocument.parse("{\"name\": \"two\"}")
      checkIsolation(
        driver,
        base.filter(filter).all,
        base.all,
        base.filter(siblingFilter).all,
        Map[String, Any]("filter" -> filter),
        Map[String, Any]("filter" -> siblingFilter)
      )
    },
    test("keeps aggregate options isolated from their base and siblings") {
      val driver = new MutableQueryPublisher
      val base   = Queries.aggregate(driver.aggregate())
      checkIsolation(
        driver,
        base.allowDiskUse(true).comment("derived").all,
        base.all,
        base.comment("sibling").all,
        Map[String, Any]("allowDiskUse" -> true, "comment" -> "derived"),
        Map[String, Any]("comment"      -> "sibling")
      )
    },
    test("keeps watch options isolated from their base and siblings") {
      val driver = new MutableQueryPublisher
      val base   = Queries.watch(driver.watch())
      val token  = BsonDocument.parse("{\"token\": 1}")
      checkIsolation(
        driver,
        base.resumeAfter(token).batchSize(1).stream.runCollect,
        base.stream.runCollect,
        base.batchSize(2).boundedStream(2).runCollect,
        Map[String, Any]("resumeAfter" -> token, "batchSize" -> 1),
        Map[String, Any]("batchSize"   -> 2)
      )
    },
    suite("every terminal allocates and configures a fresh publisher for each execution")(
      terminals.map { case (name, run) =>
        test(name) {
          val driver                    = new MutableQueryPublisher
          val task                      = run(driver)
          val createdBeforeExecution    = driver.created.get()
          val configuredBeforeExecution = driver.configured.get()

          for {
            _ <- task
            _ <- task
            _ <- ZIO.foreachPar(1 to 8)(_ => task)
          } yield assertTrue(
            createdBeforeExecution == 0,
            configuredBeforeExecution == 0,
            driver.created.get() == 10,
            driver.configured.get() == 10,
            driver.snapshots.size == 10,
            driver.snapshots.map(_.id).distinct.size == 10,
            driver.snapshots.forall(_.options == Map((if (name.startsWith("find.")) "limit" else "batchSize") -> 2))
          )
        }
      }
    ),
    test("reports factory failures in the Task error channel") {
      val error = new IllegalArgumentException("invalid query")
      val task  = Queries.find[String](throw error).all
      assertZIO(task.exit)(fails(equalTo(error)))
    },
    test("reports factory failures in the stream error channel") {
      val error  = new IllegalArgumentException("invalid watch")
      val stream = Queries.watch[String](throw error).boundedStream(2)
      assertZIO(stream.runCollect.exit)(fails(equalTo(error)))
    }
  ) @@ TestAspect.timeout(10.seconds)

  private def checkIsolation(
      driver: MutableQueryPublisher,
      derived: Task[Any],
      base: Task[Any],
      sibling: Task[Any],
      derivedOptions: Map[String, Any],
      siblingOptions: Map[String, Any]
  ): Task[TestResult] = {
    val createdBeforeExecution = driver.created.get()
    for {
      _ <- derived
      _ <- base
      _ <- sibling
      _ <- base
    } yield assertTrue(
      createdBeforeExecution == 0,
      driver.snapshots.map(_.options) == List(derivedOptions, Map.empty, siblingOptions, Map.empty),
      driver.snapshots.map(_.id).distinct.size == 4
    )
  }
}
