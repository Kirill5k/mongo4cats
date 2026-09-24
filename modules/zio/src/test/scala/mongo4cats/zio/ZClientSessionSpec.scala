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

import com.mongodb.ReadConcern
import mongo4cats.client.ClientSessionStub
import mongo4cats.models.client.TransactionOptions
import zio.{durationInt, Promise, Schedule, Scope, Task, ZIO}
import zio.test._

import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}

object ZClientSessionSpec extends ZIOSpecDefault {

  private val options = TransactionOptions.builder.readConcern(ReadConcern.SNAPSHOT).build()
  private val starters: List[(String, TransactionOptions, ZClientSession => Task[Unit])] = List(
    ("default options", TransactionOptions(), _.startTransaction),
    ("explicit options", options, _.startTransaction(options))
  )

  override def spec: Spec[TestEnvironment with Scope, Any] = suite("ZClientSession.startTransaction")(
    starters.map { case (name, expectedOptions, start) =>
      suite(name)(
        test("does not call the driver when the effect is constructed") {
          val calls = new AtomicInteger()
          val _     = start(session { _ =>
            calls.incrementAndGet()
            ()
          })

          assertTrue(calls.get() == 0)
        },
        test("waits until execution reaches the transaction effect") {
          val calls       = new AtomicInteger()
          val transaction = start(session { _ =>
            calls.incrementAndGet()
            ()
          })

          for {
            waiting <- Promise.make[Nothing, Unit]
            release <- Promise.make[Nothing, Unit]
            fiber   <- (waiting.succeed(()) *> release.await *> transaction).fork
            _       <- waiting.await
            before  <- ZIO.succeed(calls.get())
            _       <- release.succeed(())
            _       <- fiber.join
          } yield assertTrue(before == 0, calls.get() == 1)
        },
        test("calls the driver with the options on every execution of the same effect") {
          val calls       = new AtomicInteger()
          val observed    = new AtomicReference[TransactionOptions]()
          val transaction = start(session { options =>
            observed.set(options)
            calls.incrementAndGet()
            ()
          })

          for {
            _     <- transaction
            first <- ZIO.succeed(calls.get())
            _     <- transaction
          } yield assertTrue(first == 1, calls.get() == 2, observed.get() == expectedOptions)
        },
        test("captures driver errors only when the effect is executed") {
          val calls       = new AtomicInteger()
          val error       = new IllegalStateException("cannot start transaction")
          val transaction = start(session { _ =>
            calls.incrementAndGet()
            throw error
          })
          val before = calls.get()

          transaction.either.map(result => assertTrue(before == 0, result == Left(error), calls.get() == 1))
        },
        test("retries the driver call after a failed execution") {
          val calls       = new AtomicInteger()
          val error       = new IllegalStateException("cannot start transaction")
          val transaction = start(session { _ =>
            if (calls.incrementAndGet() == 1) throw error
          })

          transaction.retry(Schedule.once).map(_ => assertTrue(calls.get() == 2))
        }
      )
    }
  ) @@ TestAspect.timeout(10.seconds)

  private def session(onStart: TransactionOptions => Unit): ZClientSession =
    new ZClientSessionLive(ClientSessionStub(onStart))
}
