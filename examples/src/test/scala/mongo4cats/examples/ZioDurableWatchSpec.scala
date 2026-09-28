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

package mongo4cats.examples

import mongo4cats.bson.Document
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import zio.{Ref, Runtime, Task, Unsafe, ZIO}
import zio.stream.ZStream

class ZioDurableWatchSpec extends AnyWordSpec with Matchers {
  import DurableWatchTestData._

  private def run[A](task: Task[A]): A =
    Unsafe.unsafe(implicit unsafe => Runtime.default.unsafe.run(task).getOrThrowFiberFailure())

  "The ZIO durable watch example" should {
    "save only after processing and reload the last checkpoint on a new invocation" in {
      val result = run(for {
        saved <- Ref.make[Option[Document]](None)
        calls <- Ref.make(Vector.empty[String])
        consume = ZioDurableWatch.runWithCheckpoint(
          saved.get,
          token =>
            ZStream.fromZIO(calls.update(_ :+ s"open:${token.flatMap(_.getString("_data"))}")).drain ++
              ZStream.fromIterable(if (token.isEmpty) List(event("one"), event("two")) else List(event("three"))),
          token => calls.update(_ :+ s"save:${token.getString("_data").get}") *> saved.set(Some(token))
        )(change => calls.update(_ :+ s"process:${change.resumeToken.getString("_data").get}"))
        _     <- consume
        first <- saved.get
        _     <- consume
        last  <- saved.get
        log   <- calls.get
      } yield (first, last, log))

      result mustBe ((
        Some(token("two")),
        Some(token("three")),
        Vector("open:None", "process:one", "save:one", "process:two", "save:two", "open:Some(two)", "process:three", "save:three")
      ))
    }

    "leave the checkpoint unchanged when processing fails and stop before the next event" in {
      val failure = new RuntimeException("handler failed")
      val result  = run(for {
        saved   <- Ref.make(List.empty[Document])
        seen    <- Ref.make(List.empty[Document])
        outcome <- ZioDurableWatch
          .runWithCheckpoint(
            ZIO.succeed(None),
            _ => ZStream.fromIterable(List(event("one"), event("two"))),
            token => saved.update(_ :+ token)
          )(change => seen.update(_ :+ change.resumeToken) *> ZIO.fail(failure))
          .either
        checkpoints <- saved.get
        processed   <- seen.get
      } yield (outcome, checkpoints, processed))

      result mustBe ((Left(failure), Nil, List(token("one"))))
    }

    "propagate failed checkpoint writes before processing another event" in {
      val failure = new RuntimeException("checkpoint write failed")
      val result  = run(for {
        seen    <- Ref.make(List.empty[Document])
        outcome <- ZioDurableWatch
          .runWithCheckpoint(
            ZIO.succeed(None),
            _ => ZStream.fromIterable(List(event("one"), event("two"))),
            _ => ZIO.fail(failure)
          )(change => seen.update(_ :+ change.resumeToken))
          .either
        processed <- seen.get
      } yield (outcome, processed))

      result mustBe ((Left(failure), List(token("one"))))
    }

    "fail before opening a stream when loading the checkpoint fails" in {
      val failure = new RuntimeException("checkpoint load failed")
      val result  = run(
        ZioDurableWatch
          .runWithCheckpoint(
            ZIO.fail(failure),
            _ => throw new AssertionError("The stream must not open"),
            _ => ZIO.unit
          )(_ => ZIO.unit)
          .either
      )

      result mustBe Left(failure)
    }
  }
}
