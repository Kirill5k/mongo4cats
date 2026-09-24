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

package mongo4cats.client

import cats.effect.{Deferred, IO}
import cats.effect.unsafe.implicits.global
import com.mongodb.ReadConcern
import mongo4cats.models.client.TransactionOptions
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}

class ClientSessionSpec extends AsyncWordSpec with Matchers {

  private val options = TransactionOptions.builder.readConcern(ReadConcern.SNAPSHOT).build()
  private val starts: List[(String, ClientSession[IO] => IO[Unit], TransactionOptions)] = List(
    ("startTransaction", _.startTransaction, TransactionOptions()),
    ("startTransaction(options)", _.startTransaction(options), options)
  )

  starts.foreach { case (name, start, expectedOptions) =>
    name should {
      "leave the driver untouched when the effect is never executed" in {
        val calls = new AtomicInteger(0)
        val session = new LiveClientSession[IO](ClientSessionStub { _ =>
          calls.incrementAndGet()
          ()
        })

        val _ = start(session)

        IO(calls.get() mustBe 0).unsafeToFuture()
      }

      "wait until execution reaches the transaction effect" in {
        val calls = new AtomicInteger(0)
        val session = new LiveClientSession[IO](ClientSessionStub { _ =>
          calls.incrementAndGet()
          ()
        })
        val program = start(session)

        (for {
          waiting <- Deferred[IO, Unit]
          release <- Deferred[IO, Unit]
          fiber   <- (waiting.complete(()) *> release.get *> program).start
          _       <- waiting.get
          before  <- IO(calls.get())
          _       <- release.complete(())
          _       <- fiber.joinWithNever
        } yield {
          before mustBe 0
          calls.get() mustBe 1
        }).unsafeToFuture()
      }

      "call the driver with the options on every execution of a saved program" in {
        val calls    = new AtomicInteger(0)
        val received = new AtomicReference[TransactionOptions]()
        val session = new LiveClientSession[IO](ClientSessionStub { options =>
          calls.incrementAndGet()
          received.set(options)
        })
        val program = start(session)

        (for {
          _     <- program
          first <- IO(calls.get())
          _     <- program
        } yield {
          first mustBe 1
          calls.get() mustBe 2
          received.get() mustBe expectedOptions
        }).unsafeToFuture()
      }

      "capture driver failures at execution and retry the driver when rerun" in {
        val calls   = new AtomicInteger(0)
        val failure = new IllegalStateException("transaction could not start")
        val session = new LiveClientSession[IO](ClientSessionStub { _ =>
          calls.incrementAndGet()
          throw failure
        })
        val program = start(session)

        calls.get() mustBe 0

        (for {
          first  <- program.attempt
          second <- program.attempt
        } yield {
          first mustBe Left(failure)
          second mustBe Left(failure)
          calls.get() mustBe 2
        }).unsafeToFuture()
      }
    }
  }
}
