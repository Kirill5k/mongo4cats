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

import cats.effect.{IO, Ref}
import cats.effect.unsafe.implicits.global
import com.mongodb.client.model.changestream.OperationType
import fs2.Stream
import mongo4cats.bson.{BsonJsonMode, Document}
import mongo4cats.bson.syntax._
import mongo4cats.models.collection.ChangeStreamDocument
import org.bson.BsonDocument
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

class DurableWatchSpec extends AnyWordSpec with Matchers {
  import DurableWatchTestData._

  "The Cats Effect durable watch example" should {
    "save only after processing and reload the last checkpoint on a new invocation" in {
      val result = (for {
        saved <- Ref.of[IO, Option[Document]](None)
        calls <- Ref.of[IO, Vector[String]](Vector.empty)
        run = DurableWatch.runWithCheckpoint(
          saved.get,
          token =>
            Stream.eval(calls.update(_ :+ s"open:${token.flatMap(_.getString("_data"))}")).drain ++
              Stream.emits(if (token.isEmpty) List(event("one"), event("two")) else List(event("three"))),
          token => calls.update(_ :+ s"save:${token.getString("_data").get}") *> saved.set(Some(token))
        )(change => calls.update(_ :+ s"process:${change.resumeToken.getString("_data").get}"))
        _     <- run
        first <- saved.get
        _     <- run
        last  <- saved.get
        log   <- calls.get
      } yield (first, last, log)).unsafeRunSync()

      result mustBe ((
        Some(token("two")),
        Some(token("three")),
        Vector("open:None", "process:one", "save:one", "process:two", "save:two", "open:Some(two)", "process:three", "save:three")
      ))
    }

    "leave the checkpoint unchanged when processing fails and stop before the next event" in {
      val failure = new RuntimeException("handler failed")
      val result  = (for {
        saved   <- Ref.of[IO, List[Document]](Nil)
        seen    <- Ref.of[IO, List[Document]](Nil)
        outcome <- DurableWatch
          .runWithCheckpoint(
            IO.pure(None),
            _ => Stream.emits(List(event("one"), event("two"))),
            token => saved.update(_ :+ token)
          )(change => seen.update(_ :+ change.resumeToken) *> IO.raiseError[Unit](failure))
          .attempt
        checkpoints <- saved.get
        processed   <- seen.get
      } yield (outcome, checkpoints, processed)).unsafeRunSync()

      result mustBe ((Left(failure), Nil, List(token("one"))))
    }

    "propagate failed checkpoint writes before processing another event" in {
      val failure = new RuntimeException("checkpoint write failed")
      val result  = (for {
        seen    <- Ref.of[IO, List[Document]](Nil)
        outcome <- DurableWatch
          .runWithCheckpoint(
            IO.pure(None),
            _ => Stream.emits(List(event("one"), event("two"))),
            _ => IO.raiseError[Unit](failure)
          )(change => seen.update(_ :+ change.resumeToken))
          .attempt
        processed <- seen.get
      } yield (outcome, processed)).unsafeRunSync()

      result mustBe ((Left(failure), List(token("one"))))
    }

    "fail before opening a stream when loading the checkpoint fails" in {
      val failure = new RuntimeException("checkpoint load failed")
      val result  = DurableWatch
        .runWithCheckpoint(
          IO.raiseError(failure),
          _ => throw new AssertionError("The stream must not open"),
          _ => IO.unit
        )(_ => IO.unit)
        .attempt
        .unsafeRunSync()

      result mustBe Left(failure)
    }
  }

  "The shared checkpoint representation" should {
    "preserve the complete BSON token in storage and canonical Extended JSON" in {
      val original =
        BsonDocument.parse("""{"_data":"opaque","_typeBits":{"$binary":{"base64":"AQI=","subType":"80"}},"count":{"$numberLong":"7"}}""")
      val token    = Document.fromJava(original)
      val saved    = DurableWatchCheckpoint.record(token)
      val restored = DurableWatchCheckpoint.token(Document.parse(saved.toJson(BsonJsonMode.Canonical)))

      restored.toBsonDocument mustBe original
    }

    "reject malformed saved checkpoints instead of treating them as missing" in
      intercept[IllegalArgumentException](DurableWatchCheckpoint.token(Document("resumeToken" := "invalid")))
  }
}

private[examples] object DurableWatchTestData {
  def token(value: String): Document = Document("_data" := value)

  def event(value: String): ChangeStreamDocument[Document] = ChangeStreamDocument(
    resumeToken = token(value),
    operationType = OperationType.INSERT,
    namespace = None,
    destinationNamespace = None,
    fullDocument = None,
    fullDocumentBeforeChange = None,
    documentKey = None,
    updateDescription = None,
    txnNumber = None,
    lsid = None,
    splitEvent = None,
    extraElements = None,
    clusterTime = None,
    wallTime = None
  )
}
